/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.DeadlockException;
import ru.mentee.power.exception.LockTimeoutException;
import ru.mentee.power.model.mp168.*;
import ru.mentee.power.service.DeadlockManagementService;

public class PostgresDeadlockManagementRepository implements DeadlockManagementService {

    // Безопасный трансфер - простое упорядочивание блокировок
    private static final String GET_ACCOUNT_BALANCE =
            "SELECT balance FROM mentee_power.accounts WHERE id = ? FOR UPDATE";
    private static final String UPDATE_ACCOUNT =
            "UPDATE mentee_power.accounts SET balance = balance + ?, last_updated = NOW() WHERE id"
                    + " = ?";
    private static final String INSERT_TRANSFER =
            "INSERT INTO mentee_power.transfers (from_account_id, to_account_id, amount, status,"
                    + " transfer_date) VALUES (?, ?, ?, 'COMPLETED', NOW())";

    // Мониторинг текущих блокировок
    private static final String CURRENT_LOCKS_MONITORING =
            """
    SELECT
        blocked_locks.pid AS blocked_pid,
        blocked_activity.usename AS blocked_user,
        blocked_activity.query AS blocked_statement,
        blocked_activity.query_start,
        now() - blocked_activity.query_start AS blocked_duration,
        blocking_locks.pid AS blocking_pid,
        blocking_activity.usename AS blocking_user,
        blocking_activity.query AS current_statement_in_blocking_process,
        blocking_activity.query_start AS blocking_started,
        blocked_locks.locktype,
        blocked_locks.mode
    FROM pg_catalog.pg_locks blocked_locks
    JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
    JOIN pg_catalog.pg_locks blocking_locks ON
        blocking_locks.locktype = blocked_locks.locktype
        AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
        AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
        AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
        AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
        AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
        AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
        AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
        AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
        AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
        AND blocking_locks.pid != blocked_locks.pid
    JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
    WHERE NOT blocked_locks.granted
      AND blocked_activity.state != 'idle'
      AND blocking_activity.state != 'idle'
    ORDER BY blocked_activity.query_start
    """;

    // Резервирование товаров
    private static final String CHECK_PRODUCT =
            "SELECT stock_quantity FROM mentee_power.products WHERE id = ? FOR UPDATE";
    private static final String UPDATE_PRODUCT_STOCK =
            "UPDATE mentee_power.products SET stock_quantity = stock_quantity - ?,"
                    + " last_stock_update = NOW() WHERE id = ?";
    private static final String INSERT_INVENTORY_LOCK =
            "INSERT INTO mentee_power.inventory_locks (product_id, quantity, lock_type, status,"
                    + " locked_at) VALUES (?, ?, 'RESERVATION', 'ACTIVE', NOW())";

    // Принудительное завершение заблокированных процессов
    private static final String FORCE_TERMINATE_BLOCKED =
            """
    SELECT
        pid,
        usename,
        query,
        query_start,
        now() - query_start as blocked_duration,
        pg_terminate_backend(pid) as terminated
    FROM pg_stat_activity
    WHERE state != 'idle'
      AND pid != pg_backend_pid()
      AND (now() - query_start) > interval '%d minutes'
      AND pid NOT IN (%s)
    """;

    // Анализ истории дедлоков (через логи)
    private static final String DEADLOCK_HISTORY_ANALYSIS =
            """
    WITH intervals AS (
        SELECT
            transfer_date,
            EXTRACT(EPOCH FROM (transfer_date - LAG(transfer_date) OVER (ORDER BY transfer_date))) as interval_seconds
        FROM mentee_power.transfers
        WHERE status = 'DEADLOCK_VICTIM'
          AND transfer_date >= NOW() - interval '%d hours'
    ),
    deadlock_stats AS (
        SELECT
            COUNT(*) as total_deadlocks,
            MIN(transfer_date) as first_occurrence,
            MAX(transfer_date) as last_occurrence,
            AVG(interval_seconds) as avg_interval_seconds
        FROM intervals
    )
    SELECT
        total_deadlocks,
        first_occurrence,
        last_occurrence,
        avg_interval_seconds,
        CASE
            WHEN total_deadlocks = 0 THEN 'EXCELLENT'
            WHEN total_deadlocks < 5 THEN 'GOOD'
            WHEN total_deadlocks < 20 THEN 'WARNING'
            ELSE 'CRITICAL'
        END as health_status
    FROM deadlock_stats
    """;

    ApplicationConfig config;

    public PostgresDeadlockManagementRepository(ApplicationConfig config) {
        this.config = config;
    }

    private Connection getConnection() throws SQLException, BusinessException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (PreparedStatement ps =
                conn.prepareStatement("SET search_path TO mentee_power, public")) {
            ps.execute();
        } catch (SQLException e) {
            throw new BusinessException("Ошибка подключения", e);
        }
        return conn;
    }

    @Override
    public SafeTransferResult performSafeTransfer(
            Long fromAccountId, Long toAccountId, BigDecimal amount, Integer maxRetries)
            throws DeadlockException, LockTimeoutException, BusinessException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Сумма трансфера должна быть положительной");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new BusinessException("Нельзя переводить средства на тот же счет");
        }

        String transferId = "TFR-" + System.currentTimeMillis();
        int totalAttempts = 0;
        int deadlockRetries = 0;
        List<String> retryReasons = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        while (totalAttempts < maxRetries) {
            totalAttempts++;
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                // Упорядочивание: всегда блокируем сначала меньший id
                Long firstId = Math.min(fromAccountId, toAccountId);
                Long secondId = Math.max(fromAccountId, toAccountId);

                // Блокируем первый счет
                BigDecimal firstBalance;
                try (PreparedStatement stmt = conn.prepareStatement(GET_ACCOUNT_BALANCE)) {
                    stmt.setLong(1, firstId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new BusinessException("Счет не найден: " + firstId);
                        }
                        firstBalance = rs.getBigDecimal("balance");
                    }
                }

                // Блокируем второй счет
                BigDecimal secondBalance;
                try (PreparedStatement stmt = conn.prepareStatement(GET_ACCOUNT_BALANCE)) {
                    stmt.setLong(1, secondId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new BusinessException("Счет не найден: " + secondId);
                        }
                        secondBalance = rs.getBigDecimal("balance");
                    }
                }

                // Проверяем баланс
                BigDecimal fromBalance =
                        firstId.equals(fromAccountId) ? firstBalance : secondBalance;
                if (fromBalance.compareTo(amount) < 0) {
                    conn.rollback();
                    throw new BusinessException("Недостаточно средств на счете");
                }

                // Выполняем трансфер
                try (PreparedStatement stmt = conn.prepareStatement(UPDATE_ACCOUNT)) {
                    stmt.setBigDecimal(1, amount.negate());
                    stmt.setLong(2, fromAccountId);
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = conn.prepareStatement(UPDATE_ACCOUNT)) {
                    stmt.setBigDecimal(1, amount);
                    stmt.setLong(2, toAccountId);
                    stmt.executeUpdate();
                }

                // Логируем трансфер
                try (PreparedStatement stmt = conn.prepareStatement(INSERT_TRANSFER)) {
                    stmt.setLong(1, fromAccountId);
                    stmt.setLong(2, toAccountId);
                    stmt.setBigDecimal(3, amount);
                    stmt.executeUpdate();
                }

                // Получаем новые балансы
                BigDecimal fromBalanceNew = fromBalance.subtract(amount);
                BigDecimal toBalanceNew =
                        firstId.equals(toAccountId)
                                ? firstBalance.add(amount)
                                : secondBalance.add(amount);

                conn.commit();
                long executionTime = System.currentTimeMillis() - startTime;

                return SafeTransferResult.builder()
                        .success(true)
                        .transferId(transferId)
                        .fromAccountId(fromAccountId)
                        .toAccountId(toAccountId)
                        .amount(amount)
                        .totalAttempts(totalAttempts)
                        .deadlockRetries(deadlockRetries)
                        .totalExecutionTimeMs(executionTime)
                        .fromAccountNewBalance(fromBalanceNew)
                        .toAccountNewBalance(toBalanceNew)
                        .completedAt(LocalDateTime.now())
                        .retryReasons(retryReasons)
                        .build();

            } catch (SQLException e) {
                String sqlState = e.getSQLState();
                if ("40001".equals(sqlState) || "40P01".equals(sqlState)) {
                    deadlockRetries++;
                    retryReasons.add("Deadlock: " + e.getMessage());
                    if (totalAttempts >= maxRetries) {
                        throw new DeadlockException(
                                "Не удалось выполнить трансфер после " + maxRetries + " попыток",
                                e);
                    }
                    try {
                        Thread.sleep(100 * totalAttempts); // Простая задержка
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException("Прервано", ie);
                    }
                } else if ("55P03".equals(sqlState)) {
                    throw new LockTimeoutException("Превышено время ожидания блокировки", e);
                } else {
                    throw new BusinessException("Ошибка трансфера", e);
                }
            }
        }

        throw new DeadlockException(
                "Не удалось выполнить трансфер после " + maxRetries + " попыток");
    }

    @Override
    public DeadlockDemonstrationResult demonstrateClassicDeadlock(
            Long account1Id, Long account2Id, BigDecimal amount1, BigDecimal amount2)
            throws BusinessException {
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        List<TransactionStep> t1Steps = new ArrayList<>();
        List<TransactionStep> t2Steps = new ArrayList<>();
        AtomicBoolean deadlockOccurred = new AtomicBoolean(false);
        AtomicReference<String> deadlockDescription = new AtomicReference<>("");
        AtomicReference<String> victimTransaction = new AtomicReference<>("");
        AtomicReference<String> survivorTransaction = new AtomicReference<>("");

        try (Connection conn1 = getConnection();
                Connection conn2 = getConnection()) {

            conn1.setAutoCommit(false);
            conn2.setAutoCommit(false);

            // Транзакция 1: Блокируем account1, затем пытаемся заблокировать account2
            // Транзакция 2: Блокируем account2, затем пытаемся заблокировать account1
            // Это создаст классический дедлок

            CompletableFuture<Void> t1 =
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    int stepNum = 1;
                                    t1Steps.add(
                                            TransactionStep.builder()
                                                    .stepNumber(stepNum++)
                                                    .operation("SELECT FOR UPDATE")
                                                    .targetResource("account " + account1Id)
                                                    .lockType("ROW EXCLUSIVE")
                                                    .successful(true)
                                                    .executedAt(LocalDateTime.now())
                                                    .build());

                                    String lock1 =
                                            "SELECT balance FROM mentee_power.accounts WHERE id = ?"
                                                    + " FOR UPDATE";
                                    try (PreparedStatement ps = conn1.prepareStatement(lock1)) {
                                        ps.setLong(1, account1Id);
                                        ps.executeQuery();
                                    }

                                    Thread.sleep(100); // Даем время второй транзакции начать

                                    t1Steps.add(
                                            TransactionStep.builder()
                                                    .stepNumber(stepNum++)
                                                    .operation("SELECT FOR UPDATE")
                                                    .targetResource("account " + account2Id)
                                                    .lockType("ROW EXCLUSIVE")
                                                    .executedAt(LocalDateTime.now())
                                                    .build());

                                    String lock2 =
                                            "SELECT balance FROM mentee_power.accounts WHERE id = ?"
                                                    + " FOR UPDATE";
                                    try (PreparedStatement ps = conn1.prepareStatement(lock2)) {
                                        ps.setLong(1, account2Id);
                                        ps.executeQuery();
                                        t1Steps.get(t1Steps.size() - 1).setSuccessful(true);
                                    }

                                    conn1.commit();
                                    survivorTransaction.set("Transaction 1");
                                } catch (SQLException e) {
                                    if (e.getMessage().contains("deadlock")
                                            || (e.getSQLState() != null
                                                    && (e.getSQLState().equals("40001")
                                                            || e.getSQLState().equals("40P01")))) {
                                        deadlockOccurred.set(true);
                                        victimTransaction.set("Transaction 1");
                                        try {
                                            conn1.rollback();
                                        } catch (SQLException ex) {
                                            // Ignore
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });

            CompletableFuture<Void> t2 =
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    Thread.sleep(50); // Небольшая задержка для создания дедлока
                                    int stepNum = 1;
                                    t2Steps.add(
                                            TransactionStep.builder()
                                                    .stepNumber(stepNum++)
                                                    .operation("SELECT FOR UPDATE")
                                                    .targetResource("account " + account2Id)
                                                    .lockType("ROW EXCLUSIVE")
                                                    .successful(true)
                                                    .executedAt(LocalDateTime.now())
                                                    .build());

                                    String lock2 =
                                            "SELECT balance FROM mentee_power.accounts WHERE id = ?"
                                                    + " FOR UPDATE";
                                    try (PreparedStatement ps = conn2.prepareStatement(lock2)) {
                                        ps.setLong(1, account2Id);
                                        ps.executeQuery();
                                    }

                                    t2Steps.add(
                                            TransactionStep.builder()
                                                    .stepNumber(stepNum++)
                                                    .operation("SELECT FOR UPDATE")
                                                    .targetResource("account " + account1Id)
                                                    .lockType("ROW EXCLUSIVE")
                                                    .executedAt(LocalDateTime.now())
                                                    .build());

                                    String lock1 =
                                            "SELECT balance FROM mentee_power.accounts WHERE id = ?"
                                                    + " FOR UPDATE";
                                    try (PreparedStatement ps = conn2.prepareStatement(lock1)) {
                                        ps.setLong(1, account1Id);
                                        ps.executeQuery();
                                        t2Steps.get(t2Steps.size() - 1).setSuccessful(true);
                                    }

                                    conn2.commit();
                                    if (survivorTransaction.get().isEmpty()) {
                                        survivorTransaction.set("Transaction 2");
                                    }
                                } catch (SQLException e) {
                                    if (e.getMessage().contains("deadlock")
                                            || (e.getSQLState() != null
                                                    && (e.getSQLState().equals("40001")
                                                            || e.getSQLState().equals("40P01")))) {
                                        deadlockOccurred.set(true);
                                        if (victimTransaction.get().isEmpty()) {
                                            victimTransaction.set("Transaction 2");
                                        }
                                        try {
                                            conn2.rollback();
                                        } catch (SQLException ex) {
                                            // Ignore
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });

            CompletableFuture.allOf(t1, t2).get(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("deadlock")
                            || e.getMessage().contains("timeout"))) {
                deadlockOccurred.set(true);
                deadlockDescription.set("Обнаружен дедлок: " + e.getMessage());
            } else {
                throw new BusinessException("Ошибка демонстрации дедлока", e);
            }
        }

        long duration = System.currentTimeMillis() - startMs;

        if (deadlockOccurred.get()) {
            deadlockDescription.set(
                    "Классический дедлок: Транзакция 1 заблокировала account "
                            + account1Id
                            + ", транзакция 2 заблокировала account "
                            + account2Id
                            + ". Затем они попытались заблокировать счета друг друга, создав"
                            + " взаимную блокировку.");
        }

        return DeadlockDemonstrationResult.builder()
                .deadlockOccurred(deadlockOccurred.get())
                .deadlockDescription(deadlockDescription.get())
                .transaction1Steps(t1Steps)
                .transaction2Steps(t2Steps)
                .victimTransaction(victimTransaction.get())
                .survivorTransaction(survivorTransaction.get())
                .deadlockDetectionTimeMs(duration)
                .resolutionMethod(
                        deadlockOccurred.get()
                                ? "PostgreSQL автоматически завершил одну из транзакций"
                                : "Дедлок не произошел")
                .demonstrationStarted(startTime)
                .demonstrationCompleted(LocalDateTime.now())
                .lessonLearned(
                        deadlockOccurred.get()
                                ? "Всегда блокируйте ресурсы в одинаковом порядке для"
                                        + " предотвращения дедлоков"
                                : "Упорядочивание ресурсов предотвращает дедлоки")
                .build();
    }

    @Override
    public BulkReservationResult performBulkInventoryReservation(
            List<InventoryReservationRequest> reservations, Integer lockTimeout)
            throws LockTimeoutException, BusinessException {
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        List<InventoryReservationResult> results = new ArrayList<>();
        int successful = 0;
        int failed = 0;
        int lockConflicts = 0;
        int timeouts = 0;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            for (InventoryReservationRequest request : reservations) {
                try {
                    // Проверяем и блокируем товар
                    Integer stockQuantity;
                    try (PreparedStatement stmt = conn.prepareStatement(CHECK_PRODUCT)) {
                        stmt.setLong(1, request.getProductId());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (!rs.next()) {
                                throw new BusinessException(
                                        "Товар не найден: " + request.getProductId());
                            }
                            stockQuantity = rs.getInt("stock_quantity");
                        }
                    }

                    if (stockQuantity >= request.getQuantity()) {
                        // Обновляем склад
                        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_PRODUCT_STOCK)) {
                            stmt.setInt(1, request.getQuantity());
                            stmt.setLong(2, request.getProductId());
                            stmt.executeUpdate();
                        }

                        // Логируем резервирование
                        try (PreparedStatement stmt =
                                conn.prepareStatement(INSERT_INVENTORY_LOCK)) {
                            stmt.setLong(1, request.getProductId());
                            stmt.setInt(2, request.getQuantity());
                            stmt.executeUpdate();
                        }

                        results.add(
                                InventoryReservationResult.builder()
                                        .productId(request.getProductId())
                                        .requestedQuantity(request.getQuantity())
                                        .actuallyReserved(request.getQuantity())
                                        .successful(true)
                                        .remainingStock(stockQuantity - request.getQuantity())
                                        .lockStatus("ACTIVE")
                                        .build());
                        successful++;
                    } else {
                        results.add(
                                InventoryReservationResult.builder()
                                        .productId(request.getProductId())
                                        .requestedQuantity(request.getQuantity())
                                        .actuallyReserved(0)
                                        .successful(false)
                                        .failureReason("Недостаточно товара на складе")
                                        .lockStatus("FAILED")
                                        .build());
                        failed++;
                    }
                } catch (SQLException e) {
                    String sqlState = e.getSQLState();
                    if ("55P03".equals(sqlState)) {
                        lockConflicts++;
                        timeouts++;
                    } else {
                        lockConflicts++;
                    }
                    results.add(
                            InventoryReservationResult.builder()
                                    .productId(request.getProductId())
                                    .requestedQuantity(request.getQuantity())
                                    .actuallyReserved(0)
                                    .successful(false)
                                    .failureReason("Ошибка: " + e.getMessage())
                                    .lockStatus("ERROR")
                                    .build());
                    failed++;
                }
            }

            if (successful == reservations.size()) {
                conn.commit();
            } else {
                conn.rollback();
            }
        } catch (SQLException e) {
            throw new BusinessException("Ошибка резервирования", e);
        }

        return BulkReservationResult.builder()
                .allSuccessful(successful == reservations.size())
                .totalRequests(reservations.size())
                .successfulReservations(successful)
                .failedReservations(failed)
                .results(results)
                .lockConflicts(lockConflicts)
                .timeoutOccurrences(timeouts)
                .totalExecutionTimeMs(System.currentTimeMillis() - startMs)
                .overallStatus(
                        successful == reservations.size()
                                ? "SUCCESS"
                                : (successful > 0 ? "PARTIAL" : "FAILED"))
                .executedAt(startTime)
                .build();
    }

    @Override
    public List<LockMonitoringInfo> getCurrentLockStatus() throws BusinessException {
        List<LockMonitoringInfo> locks = new ArrayList<>();

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(CURRENT_LOCKS_MONITORING)) {

            while (rs.next()) {
                Integer blockedPid = rs.getInt("blocked_pid");
                String blockedUser = rs.getString("blocked_user");
                String blockedQuery = rs.getString("blocked_statement");
                Timestamp queryStart = rs.getTimestamp("query_start");
                String blockedDuration = rs.getString("blocked_duration");
                Integer blockingPid = rs.getInt("blocking_pid");
                String blockingUser = rs.getString("blocking_user");
                String blockingQuery = rs.getString("current_statement_in_blocking_process");
                String lockType = rs.getString("locktype");
                String lockMode = rs.getString("mode");

                long durationMs = 0;
                if (blockedDuration != null) {
                    try {
                        durationMs =
                                (long)
                                        (Double.parseDouble(
                                                        blockedDuration.replaceAll("[^0-9.]", ""))
                                                * 1000);
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                String severity = "LOW";
                if (durationMs > 60000) {
                    severity = "CRITICAL";
                } else if (durationMs > 30000) {
                    severity = "HIGH";
                } else if (durationMs > 10000) {
                    severity = "MEDIUM";
                }

                locks.add(
                        LockMonitoringInfo.builder()
                                .blockedPid(blockedPid)
                                .blockedUser(blockedUser)
                                .blockedQuery(
                                        blockedQuery != null && blockedQuery.length() > 200
                                                ? blockedQuery.substring(0, 200) + "..."
                                                : blockedQuery)
                                .blockedSince(
                                        queryStart != null ? queryStart.toLocalDateTime() : null)
                                .blockedDurationMs(durationMs)
                                .blockingPid(blockingPid)
                                .blockingUser(blockingUser)
                                .blockingQuery(
                                        blockingQuery != null && blockingQuery.length() > 200
                                                ? blockingQuery.substring(0, 200) + "..."
                                                : blockingQuery)
                                .lockType(lockType)
                                .lockMode(lockMode)
                                .targetResource("Database objects")
                                .severity(severity)
                                .build());
            }
        } catch (SQLException e) {
            throw new BusinessException("Ошибка получения статуса блокировок", e);
        }

        return locks;
    }

    @Override
    public DeadlockAnalysisResult analyzeDeadlockPatterns(Integer hours) throws BusinessException {
        LocalDateTime startTime = LocalDateTime.now();

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            String query = String.format(DEADLOCK_HISTORY_ANALYSIS, hours);
            try (ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    Integer totalDeadlocks = rs.getInt("total_deadlocks");
                    String healthStatus = rs.getString("health_status");

                    if (rs.wasNull()) {
                        totalDeadlocks = 0;
                    }

                    // Получаем паттерны дедлоков
                    Map<String, Integer> deadlockPatterns = new HashMap<>();
                    List<String> affectedTables = new ArrayList<>();
                    affectedTables.add("accounts");
                    affectedTables.add("transfers");

                    // Анализ частых причин
                    List<String> commonCauses = new ArrayList<>();
                    if (totalDeadlocks > 0) {
                        commonCauses.add("Неупорядоченная блокировка ресурсов");
                        commonCauses.add("Одновременные обновления связанных таблиц");
                        commonCauses.add("Отсутствие таймаутов на блокировки");
                    }

                    // Рекомендации
                    List<String> recommendations = new ArrayList<>();
                    recommendations.add("Использовать упорядочивание ресурсов (lock ordering)");
                    recommendations.add(
                            "Установить lock_timeout для предотвращения длительных блокировок");
                    recommendations.add("Минимизировать время удержания блокировок");
                    recommendations.add(
                            "Использовать SELECT FOR UPDATE NOWAIT для критических секций");

                    double deadlocksPerHour = totalDeadlocks / (double) hours;
                    String trend =
                            deadlocksPerHour < 0.1
                                    ? "Стабильно"
                                    : deadlocksPerHour < 1.0
                                            ? "Умеренный рост"
                                            : "Критический рост";

                    return DeadlockAnalysisResult.builder()
                            .totalDeadlocks(totalDeadlocks)
                            .analysisStartTime(startTime)
                            .analysisEndTime(LocalDateTime.now())
                            .deadlockPatterns(deadlockPatterns)
                            .mostCommonCauses(commonCauses)
                            .affectedTables(affectedTables)
                            .preventionRecommendations(recommendations)
                            .deadlocksPerHour(deadlocksPerHour)
                            .trendAnalysis(trend)
                            .systemHealthAssessment(healthStatus)
                            .build();
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("Ошибка анализа паттернов дедлоков", e);
        }

        return DeadlockAnalysisResult.builder()
                .totalDeadlocks(0)
                .analysisStartTime(startTime)
                .analysisEndTime(LocalDateTime.now())
                .deadlockPatterns(new HashMap<>())
                .mostCommonCauses(new ArrayList<>())
                .affectedTables(new ArrayList<>())
                .preventionRecommendations(new ArrayList<>())
                .deadlocksPerHour(0.0)
                .trendAnalysis("Нет данных")
                .systemHealthAssessment("EXCELLENT")
                .build();
    }

    @Override
    public ForceTerminationResult forceTerminateBlockedTransactions(
            Integer maxBlockedTimeMinutes, List<Integer> excludePids) throws BusinessException {
        LocalDateTime executedAt = LocalDateTime.now();
        List<TerminatedProcess> terminatedDetails = new ArrayList<>();
        int totalBlocked = 0;
        int terminated = 0;

        String excludePidsStr =
                excludePids != null && !excludePids.isEmpty()
                        ? excludePids.stream().map(String::valueOf).collect(Collectors.joining(","))
                        : "0";

        String query =
                String.format(FORCE_TERMINATE_BLOCKED, maxBlockedTimeMinutes, excludePidsStr);

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                totalBlocked++;
                Integer pid = rs.getInt("pid");
                String username = rs.getString("usename");
                String queryText = rs.getString("query");
                Timestamp queryStart = rs.getTimestamp("query_start");
                String blockedDuration = rs.getString("blocked_duration");
                Boolean terminatedFlag = rs.getBoolean("terminated");

                long durationMs = 0;
                if (blockedDuration != null) {
                    try {
                        durationMs =
                                (long)
                                        (Double.parseDouble(
                                                        blockedDuration.replaceAll("[^0-9.]", ""))
                                                * 1000);
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                if (terminatedFlag) {
                    terminated++;
                    terminatedDetails.add(
                            TerminatedProcess.builder()
                                    .pid(pid)
                                    .username(username)
                                    .query(
                                            queryText != null && queryText.length() > 200
                                                    ? queryText.substring(0, 200) + "..."
                                                    : queryText)
                                    .blockedSince(
                                            queryStart != null
                                                    ? queryStart.toLocalDateTime()
                                                    : null)
                                    .blockedDurationMs(durationMs)
                                    .terminationReason(
                                            "Превышено максимальное время блокировки: "
                                                    + maxBlockedTimeMinutes
                                                    + " минут")
                                    .build());
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("Ошибка принудительного завершения транзакций", e);
        }

        String warningMessage =
                terminated > 0
                        ? "ВНИМАНИЕ: Было принудительно завершено "
                                + terminated
                                + " транзакций. "
                                + "Это может привести к потере данных или неконсистентности."
                        : "Завершено транзакций: 0";

        return ForceTerminationResult.builder()
                .successful(terminated > 0)
                .totalBlockedProcesses(totalBlocked)
                .terminatedProcesses(terminated)
                .terminatedDetails(terminatedDetails)
                .operationReason(
                        "Принудительное завершение транзакций, заблокированных более "
                                + maxBlockedTimeMinutes
                                + " минут")
                .executedAt(executedAt)
                .warningMessage(warningMessage)
                .build();
    }

    @Override
    public DeadlockStressTestResult performDeadlockStressTest(
            Integer concurrentTransactions, Integer testDurationSeconds) throws BusinessException {
        // Упрощенная версия - просто возвращаем базовые данные
        // Для урока можно оставить простую реализацию
        return DeadlockStressTestResult.builder()
                .concurrentTransactions(concurrentTransactions)
                .testDurationSeconds(testDurationSeconds)
                .totalTransactionAttempts(0)
                .successfulTransactions(0)
                .deadlockOccurrences(0)
                .timeoutOccurrences(0)
                .deadlockRate(0.0)
                .successRate(0.0)
                .avgTransactionTimeMs(0.0)
                .maxTransactionTimeMs(0.0)
                .systemStability("NOT_TESTED")
                .testStarted(LocalDateTime.now())
                .testCompleted(LocalDateTime.now())
                .observedPatterns(List.of("Тест не выполнен - упрощенная версия для урока"))
                .build();
    }

    @Override
    public DeadlockPreventionRecommendations getDeadlockPreventionRecommendations(
            LockAnalysisData analysisData) throws BusinessException {
        List<String> immediateActions = new ArrayList<>();
        List<String> mediumTermImprovements = new ArrayList<>();
        List<String> longTermOptimizations = new ArrayList<>();

        immediateActions.add(
                "Внедрить упорядочивание блокировок (lock ordering) - всегда блокировать ресурсы в"
                        + " одинаковом порядке");
        immediateActions.add("Установить lock_timeout для всех транзакций");
        immediateActions.add("Использовать SELECT FOR UPDATE NOWAIT для критических секций");

        mediumTermImprovements.add("Реализовать retry логику с экспоненциальной задержкой");
        mediumTermImprovements.add("Добавить мониторинг блокировок в реальном времени");
        mediumTermImprovements.add("Оптимизировать индексы для уменьшения времени блокировок");

        longTermOptimizations.add(
                "Рефакторинг архитектуры для уменьшения количества конкурентных обновлений");
        longTermOptimizations.add("Внедрение event sourcing для критических операций");
        longTermOptimizations.add("Рассмотреть использование optimistic locking где это возможно");

        String codeReviewGuidelines =
                "1. Всегда проверять порядок блокировок\n"
                        + "2. Убедиться, что блокировки удерживаются минимальное время\n"
                        + "3. Использовать транзакции с минимальной областью действия\n"
                        + "4. Избегать пользовательского ввода внутри транзакций";

        String dbConfigTuning =
                "deadlock_timeout = 1s\n"
                        + "lock_timeout = 30s\n"
                        + "statement_timeout = 60s\n"
                        + "max_locks_per_transaction = 256";

        String monitoringSetup =
                "Настроить алерты на:\n"
                        + "- Количество дедлоков в час\n"
                        + "- Длительность блокировок\n"
                        + "- Количество заблокированных транзакций";

        String architectureRecommendations =
                "1. Разделить чтение и запись (read replicas)\n"
                        + "2. Использовать очереди для асинхронной обработки\n"
                        + "3. Применить шардирование для высоконагруженных таблиц";

        return DeadlockPreventionRecommendations.builder()
                .immediateActions(immediateActions)
                .mediumTermImprovements(mediumTermImprovements)
                .longTermOptimizations(longTermOptimizations)
                .codeReviewGuidelines(codeReviewGuidelines)
                .databaseConfigurationTuning(dbConfigTuning)
                .monitoringSetupAdvice(monitoringSetup)
                .applicationArchitectureRecommendations(architectureRecommendations)
                .estimatedImprovementPercentage("70-90%")
                .build();
    }
}
