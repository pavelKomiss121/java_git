/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp166.*;
import ru.mentee.power.repository.interfaces.IsolationLevelRepository;
import ru.mentee.power.service.IsolationLevelService;

public class PostgresIsolationLevelRepository
        implements IsolationLevelRepository, IsolationLevelService {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresIsolationLevelRepository.class);
    private final ApplicationConfig config;

    public PostgresIsolationLevelRepository(ApplicationConfig config) {
        this.config = config;
    }

    private Connection getConnection() throws DataAccessException, SQLException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (PreparedStatement statement =
                conn.prepareStatement("SET search_path TO mentee_power, public")) {
            statement.execute();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка соединения", e);
        }
        return conn;
    }

    /**
     * Преобразует строковое значение уровня изоляции в константу Connection.
     */
    private int parseIsolationLevel(String isolationLevel) {
        return switch (isolationLevel.toUpperCase()) {
            case "READ UNCOMMITTED" -> Connection.TRANSACTION_READ_UNCOMMITTED;
            case "READ COMMITTED" -> Connection.TRANSACTION_READ_COMMITTED;
            case "REPEATABLE READ" -> Connection.TRANSACTION_REPEATABLE_READ;
            case "SERIALIZABLE" -> Connection.TRANSACTION_SERIALIZABLE;
            default -> {
                log.warn(
                        "Неизвестный уровень изоляции: {}. Используется READ COMMITTED",
                        isolationLevel);
                yield Connection.TRANSACTION_READ_COMMITTED;
            }
        };
    }

    @Override
    public <T> T executeWithIsolationLevel(String isolationLevel, TransactionOperation<T> operation)
            throws DataAccessException {
        log.debug("Выполнение операции с уровнем изоляции: {}", isolationLevel);
        long startTime = System.currentTimeMillis();

        try (Connection conn = getConnection()) {
            int isolationLevelInt = parseIsolationLevel(isolationLevel);
            conn.setTransactionIsolation(isolationLevelInt);
            conn.setAutoCommit(false);

            log.debug(
                    "Установлен уровень изоляции: {} (код: {})", isolationLevel, isolationLevelInt);

            try {
                T result = operation.execute(conn);
                conn.commit();
                long duration = System.currentTimeMillis() - startTime;
                log.debug("Операция успешно выполнена за {} мс", duration);
                return result;
            } catch (SQLException e) {
                conn.rollback();
                log.error("Ошибка выполнения операции. Транзакция откачена", e);
                throw new DataAccessException(
                        "Ошибка выполнения операции с уровнем изоляции: " + isolationLevel, e);
            }
        } catch (SQLException e) {
            log.error("Ошибка создания соединения для выполнения операции", e);
            throw new DataAccessException("Ошибка создания соединения", e);
        }
    }

    @Override
    public TransactionContext startTransactionWithLevel(String isolationLevel) {
        return null;
    }

    @Override
    public OperationResult performConcurrentOperation(
            TransactionContext context, String operation, Map<String, Object> params) {
        return null;
    }

    /**
     * Получает баланс счета.
     */
    private BigDecimal getAccountBalance(Connection conn, Long accountId) throws SQLException {
        String sql = "SELECT balance FROM mentee_power.accounts WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Обновляет баланс счета.
     */
    private void updateAccountBalance(Connection conn, Long accountId, BigDecimal amount)
            throws SQLException {
        String sql =
                "UPDATE mentee_power.accounts SET balance = balance + ?, updated_at = NOW() WHERE"
                        + " id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBigDecimal(1, amount);
            stmt.setLong(2, accountId);
            stmt.executeUpdate();
        }
    }

    @Override
    public DirtyReadResult demonstrateDirtyReads(Long accountId) {
        log.info(
                "Демонстрация Dirty Reads для счета {} через executeWithIsolationLevel", accountId);
        LocalDateTime operationStartTime = LocalDateTime.now();

        try {
            // Первое соединение - для изменения данных (без коммита)
            BigDecimal initialBalance;
            BigDecimal intermediateBalance;
            BigDecimal finalBalance;
            boolean dirtyReadDetected = false;

            try (Connection conn1 = getConnection()) {
                conn1.setAutoCommit(false);
                initialBalance = getAccountBalance(conn1, accountId);

                // Изменяем баланс, но не коммитим
                updateAccountBalance(conn1, accountId, new BigDecimal("500.00"));

                // Второе соединение - для чтения с READ UNCOMMITTED
                try {
                    DirtyReadResult result =
                            executeWithIsolationLevel(
                                    "READ UNCOMMITTED",
                                    (Connection conn2) -> {
                                        // Читаем незафиксированные данные (dirty read)
                                        BigDecimal dirtyBalance =
                                                getAccountBalance(conn2, accountId);
                                        return DirtyReadResult.builder()
                                                .sessionId("session-2")
                                                .isolationLevel("READ UNCOMMITTED")
                                                .initialBalance(initialBalance)
                                                .intermediateBalance(dirtyBalance)
                                                .intermediateReadTime(LocalDateTime.now())
                                                .build();
                                    });

                    intermediateBalance = result.getIntermediateBalance();

                    conn1.rollback();

                    finalBalance = getAccountBalance(conn1, accountId);
                    conn1.commit();

                    dirtyReadDetected =
                            !intermediateBalance.equals(initialBalance)
                                    && !intermediateBalance.equals(finalBalance);

                    return DirtyReadResult.builder()
                            .sessionId("session-2")
                            .isolationLevel("READ UNCOMMITTED")
                            .initialBalance(initialBalance)
                            .intermediateBalance(intermediateBalance)
                            .finalBalance(finalBalance)
                            .dirtyReadDetected(dirtyReadDetected)
                            .operationStartTime(operationStartTime)
                            .intermediateReadTime(result.getIntermediateReadTime())
                            .operationEndTime(LocalDateTime.now())
                            .build();

                } catch (DataAccessException e) {
                    conn1.rollback();
                    throw e;
                }
            }
        } catch (DataAccessException | SQLException e) {
            log.error("Ошибка демонстрации dirty reads", e);
            return DirtyReadResult.builder()
                    .sessionId("error")
                    .isolationLevel("READ UNCOMMITTED")
                    .dirtyReadDetected(false)
                    .operationStartTime(operationStartTime)
                    .operationEndTime(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public NonRepeatableReadResult demonstrateNonRepeatableReads(Long accountId) {
        log.info(
                "Демонстрация Non-repeatable Reads для счета {} через executeWithIsolationLevel",
                accountId);
        LocalDateTime firstReadTime = LocalDateTime.now();

        try {
            NonRepeatableReadResult result =
                    executeWithIsolationLevel(
                            "READ COMMITTED",
                            (Connection conn) -> {
                                BigDecimal firstRead = getAccountBalance(conn, accountId);
                                LocalDateTime firstReadTimeLocal = LocalDateTime.now();

                                try (Connection conn2 = getConnection()) {
                                    conn2.setAutoCommit(false);
                                    updateAccountBalance(
                                            conn2, accountId, new BigDecimal("300.00"));
                                    conn2.commit();
                                } catch (SQLException | DataAccessException e) {
                                    log.error(
                                            "Ошибка изменения баланса в параллельной транзакции",
                                            e);
                                }

                                BigDecimal secondRead = getAccountBalance(conn, accountId);
                                LocalDateTime secondReadTime = LocalDateTime.now();

                                boolean nonRepeatableReadDetected = !firstRead.equals(secondRead);

                                return NonRepeatableReadResult.builder()
                                        .sessionId("session-1")
                                        .isolationLevel("READ COMMITTED")
                                        .accountId(accountId)
                                        .firstReadBalance(firstRead)
                                        .secondReadBalance(secondRead)
                                        .nonRepeatableReadDetected(nonRepeatableReadDetected)
                                        .firstReadTime(firstReadTimeLocal)
                                        .secondReadTime(secondReadTime)
                                        .concurrentTransactionId("tx-2")
                                        .build();
                            });
            return result;
        } catch (DataAccessException e) {
            log.error("Ошибка демонстрации non-repeatable reads", e);
            return NonRepeatableReadResult.builder()
                    .sessionId("error")
                    .isolationLevel("READ COMMITTED")
                    .accountId(accountId)
                    .nonRepeatableReadDetected(false)
                    .firstReadTime(firstReadTime)
                    .secondReadTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Подсчитывает количество транзакций по критерию.
     */
    private Integer countTransactions(Connection conn, Long accountId, BigDecimal threshold)
            throws SQLException {
        String sql =
                "SELECT COUNT(*) as cnt FROM mentee_power.transactions "
                        + "WHERE account_id = ? AND amount >= ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            stmt.setBigDecimal(2, threshold);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        }
        return 0;
    }

    /**
     * Создает новую транзакцию.
     */
    private void createTransaction(Connection conn, Long accountId, BigDecimal amount)
            throws SQLException {
        String sql =
                "INSERT INTO mentee_power.transactions "
                        + "(account_id, amount, transaction_type, status, description, created_at) "
                        + "VALUES (?, ?, 'DEPOSIT', 'COMPLETED', 'Test transaction', NOW())";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            stmt.setBigDecimal(2, amount);
            stmt.executeUpdate();
        }
    }

    @Override
    public PhantomReadResult demonstratePhantomReads(String status) {
        log.info(
                "Демонстрация Phantom Reads для статуса {} через executeWithIsolationLevel",
                status);
        LocalDateTime firstReadTime = LocalDateTime.now();

        Long accountId = 1L;
        BigDecimal threshold = new BigDecimal("1000.00");

        try {
            PhantomReadResult result =
                    executeWithIsolationLevel(
                            "REPEATABLE READ",
                            (Connection conn) -> {
                                Integer firstCount = countTransactions(conn, accountId, threshold);
                                LocalDateTime firstReadTimeLocal = LocalDateTime.now();

                                // Параллельно создаем новую транзакцию в другом соединении
                                try (Connection conn2 = getConnection()) {
                                    conn2.setAutoCommit(false);
                                    createTransaction(conn2, accountId, new BigDecimal("1500.00"));
                                    conn2.commit();
                                } catch (SQLException | DataAccessException e) {
                                    log.error(
                                            "Ошибка создания транзакции в параллельном соединении",
                                            e);
                                }

                                Integer secondCount = countTransactions(conn, accountId, threshold);
                                LocalDateTime secondReadTime = LocalDateTime.now();

                                boolean phantomReadDetected = !firstCount.equals(secondCount);
                                Integer newRecordsCount = secondCount - firstCount;

                                return PhantomReadResult.builder()
                                        .sessionId("session-1")
                                        .isolationLevel("REPEATABLE READ")
                                        .firstReadCount(firstCount)
                                        .secondReadCount(secondCount)
                                        .phantomReadDetected(phantomReadDetected)
                                        .firstReadTime(firstReadTimeLocal)
                                        .secondReadTime(secondReadTime)
                                        .newRecordsCount(newRecordsCount)
                                        .query(
                                                "SELECT COUNT(*) FROM transactions WHERE account_id"
                                                        + " = ? AND amount >= ?")
                                        .build();
                            });

            return result;
        } catch (DataAccessException e) {
            log.error("Ошибка демонстрации phantom reads", e);
            return PhantomReadResult.builder()
                    .sessionId("error")
                    .isolationLevel("REPEATABLE READ")
                    .phantomReadDetected(false)
                    .firstReadTime(firstReadTime)
                    .secondReadTime(LocalDateTime.now())
                    .query("SELECT COUNT(*) FROM transactions WHERE account_id = ? AND amount >= ?")
                    .build();
        }
    }

    @Override
    public ConcurrentBookingResult performConcurrentBooking(
            Long productId, Long userId, Integer quantity, String isolationLevel) {
        log.info(
                "Конкурентное бронирование продукта {} пользователем {} в количестве {} с уровнем"
                        + " изоляции {}",
                productId,
                userId,
                quantity,
                isolationLevel);

        try {
            return executeWithIsolationLevel(
                    isolationLevel,
                    (Connection conn) -> {
                        String checkSql =
                                "SELECT stock_quantity FROM mentee_power.products WHERE id = ? FOR"
                                        + " UPDATE";
                        Integer availableQuantity = null;

                        try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                            stmt.setLong(1, productId);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) {
                                    availableQuantity = rs.getInt("stock_quantity");
                                }
                            }
                        }

                        if (availableQuantity == null || availableQuantity < quantity) {
                            return ConcurrentBookingResult.builder()
                                    .requestedQuantity(quantity)
                                    .actualReservedQuantity(0)
                                    .stockAfterOperation(
                                            availableQuantity != null ? availableQuantity : 0)
                                    .bookingStatus("FAILED")
                                    .concurrencyIssues(
                                            java.util.List.of("Недостаточно товара на складе"))
                                    .build();
                        }

                        String updateSql =
                                "UPDATE mentee_power.products SET stock_quantity = stock_quantity -"
                                        + " ? WHERE id = ?";
                        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                            stmt.setInt(1, quantity);
                            stmt.setLong(2, productId);
                            int rowsAffected = stmt.executeUpdate();

                            if (rowsAffected > 0) {
                                Integer stockAfter = availableQuantity - quantity;
                                return ConcurrentBookingResult.builder()
                                        .requestedQuantity(quantity)
                                        .actualReservedQuantity(quantity)
                                        .stockAfterOperation(stockAfter)
                                        .bookingStatus("SUCCESS")
                                        .concurrencyIssues(java.util.List.of())
                                        .build();
                            }
                        }

                        return ConcurrentBookingResult.builder()
                                .requestedQuantity(quantity)
                                .actualReservedQuantity(0)
                                .stockAfterOperation(availableQuantity)
                                .bookingStatus("FAILED")
                                .concurrencyIssues(
                                        java.util.List.of("Не удалось обновить количество товара"))
                                .build();
                    });
        } catch (DataAccessException e) {
            log.error("Ошибка конкурентного бронирования", e);
            return ConcurrentBookingResult.builder()
                    .requestedQuantity(quantity)
                    .actualReservedQuantity(0)
                    .stockAfterOperation(0)
                    .bookingStatus("FAILED")
                    .concurrencyIssues(
                            java.util.List.of("Ошибка выполнения бронирования: " + e.getMessage()))
                    .build();
        }
    }

    @Override
    public ConcurrencySimulationResult simulateHighConcurrency(
            Integer users, Integer operations, String isolationLevel) {
        log.info(
                "Симуляция высокой конкурентности: {} пользователей, {} операций, уровень изоляции"
                        + " {}",
                users,
                operations,
                isolationLevel);

        long startTime = System.currentTimeMillis();
        int successfulOperations = 0;
        int conflictsDetected = 0;

        // Упрощенная симуляция: выполняем операции последовательно с заданным уровнем изоляции
        for (int i = 0; i < operations; i++) {
            try {
                Long accountId = 1L; // Используем тестовый счет
                final int operationNumber = i;

                executeWithIsolationLevel(
                        isolationLevel,
                        (Connection conn) -> {
                            // Простая операция: чтение и обновление баланса
                            getAccountBalance(conn, accountId); // Читаем для проверки
                            updateAccountBalance(conn, accountId, new BigDecimal("10.00"));
                            log.debug("Операция {} выполнена успешно", operationNumber);
                            return null;
                        });

                successfulOperations++;
            } catch (DataAccessException e) {
                String errorCode =
                        e.getCause() instanceof SQLException
                                ? ((SQLException) e.getCause()).getSQLState()
                                : null;

                // Проверяем на конфликты сериализации
                if ("40001".equals(errorCode)) {
                    conflictsDetected++;
                }
                log.debug("Операция {} завершилась ошибкой: {}", i, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        double successRate = operations > 0 ? (double) successfulOperations / operations * 100 : 0;
        double averageResponseTime = operations > 0 ? (double) duration / operations : 0;

        return ConcurrencySimulationResult.builder()
                .totalOperations(operations)
                .successRate(successRate)
                .averageResponseTime(averageResponseTime)
                .deadlockCount(0) // Упрощенная версия не отслеживает deadlocks
                .serializationFailureCount(conflictsDetected)
                .build();
    }
}
