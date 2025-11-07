/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp167.ConcurrencyAnomalyResult;
import ru.mentee.power.model.mp167.MoneyTransferResult;
import ru.mentee.power.repository.interfaces.ConcurrencyProblemsRepository;

public class PostgresConcurrencyProblemsRepository implements ConcurrencyProblemsRepository {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresConcurrencyProblemsRepository.class);
    private final ApplicationConfig config;

    public PostgresConcurrencyProblemsRepository(ApplicationConfig config) {
        this.config = config;
    }

    protected Connection getConnection() throws DataAccessException, SQLException {
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
    public ConcurrencyAnomalyResult demonstrateDirtyRead(Long accountId, BigDecimal amountToChange)
            throws DataAccessException {
        log.info(
                "Начало демонстрации Dirty Read для счета {} с изменением на {}",
                accountId,
                amountToChange);
        LocalDateTime executionTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String isolationLevel = "READ UNCOMMITTED";
        List<String> steps = new ArrayList<>();

        try (Connection conn1 = getConnection();
                Connection conn2 = getConnection()) {

            // Сессия 2: Устанавливаем READ UNCOMMITTED ДО начала транзакции
            conn2.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            conn2.setAutoCommit(false);

            // Начальное значение
            conn1.setAutoCommit(false);
            BigDecimal initialBalance = getAccountBalance(conn1, accountId);
            steps.add("1. Сессия 1: Читаем начальный баланс = " + initialBalance);

            // Сессия 2: Читаем баланс
            BigDecimal balanceBeforeChange = getAccountBalance(conn2, accountId);
            steps.add("2. Сессия 2 (READ UNCOMMITTED): Читаем баланс = " + balanceBeforeChange);

            // Сессия 1: Изменяем баланс, но не коммитим
            updateAccountBalance(conn1, accountId, amountToChange);
            steps.add("3. Сессия 1: Изменяем баланс на " + amountToChange + " (БЕЗ COMMIT)");

            // Сессия 2: Читаем снова (dirty read)
            BigDecimal dirtyBalance = getAccountBalance(conn2, accountId);
            steps.add("4. Сессия 2: Читаем баланс снова = " + dirtyBalance + " (DIRTY READ!)");

            // Сессия 1: Откатываем транзакцию
            conn1.rollback();
            steps.add("5. Сессия 1: ROLLBACK - отменяем изменения");

            // Сессия 2: Читаем финальный баланс
            BigDecimal finalBalance = getAccountBalance(conn2, accountId);
            steps.add("6. Сессия 2: Читаем финальный баланс = " + finalBalance);

            conn2.commit();

            boolean anomalyDetected =
                    !dirtyBalance.equals(initialBalance) && !dirtyBalance.equals(finalBalance);

            long duration = System.currentTimeMillis() - startTime;
            log.info(
                    "Dirty Read демонстрация завершена. Аномалия обнаружена: {}. Время выполнения:"
                            + " {} мс",
                    anomalyDetected,
                    duration);

            List<String> recommendations =
                    Arrays.asList(
                            "Используйте уровень изоляции READ COMMITTED или выше",
                            "Избегайте чтения незафиксированных данных",
                            "Всегда проверяйте данные после COMMIT");

            return ConcurrencyAnomalyResult.builder()
                    .anomalyType("DIRTY_READ")
                    .isolationLevel(isolationLevel)
                    .anomalyDetected(anomalyDetected)
                    .detailedDescription(
                            anomalyDetected
                                    ? "Обнаружен dirty read: Сессия 2 увидела незафиксированные"
                                            + " данные ("
                                            + dirtyBalance
                                            + "), которые были отменены. Финальный баланс остался "
                                            + finalBalance
                                    : "Dirty read не обнаружен при текущих настройках")
                    .executionSteps(steps)
                    .initialValue(initialBalance.toString())
                    .intermediateValue(dirtyBalance.toString())
                    .finalValue(finalBalance.toString())
                    .executionTime(executionTime)
                    .executionDurationMillis(duration)
                    .preventionRecommendations(recommendations)
                    .build();

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка демонстрации dirty read", e);
        }
    }

    @Override
    public ConcurrencyAnomalyResult demonstrateNonRepeatableRead(
            Long accountId, BigDecimal amountToChange) throws DataAccessException {
        log.info(
                "Начало демонстрации Non-repeatable Read для счета {} с изменением на {}",
                accountId,
                amountToChange);
        LocalDateTime executionTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String isolationLevel = "READ COMMITTED";
        List<String> steps = new ArrayList<>();

        try (Connection conn1 = getConnection();
                Connection conn2 = getConnection()) {

            // Устанавливаем уровень изоляции ДО начала транзакций
            conn1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn2.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            // Сессия 1: Начинает транзакцию с READ COMMITTED
            conn1.setAutoCommit(false);
            BigDecimal firstRead = getAccountBalance(conn1, accountId);
            steps.add("1. Сессия 1 (READ COMMITTED): Первое чтение баланса = " + firstRead);

            // Сессия 2: Изменяет баланс и коммитит
            conn2.setAutoCommit(false);
            updateAccountBalance(conn2, accountId, amountToChange);
            conn2.commit();
            steps.add("2. Сессия 2: Изменяем баланс на " + amountToChange + " и COMMIT");

            // Сессия 1: Читает баланс снова (видит изменения)
            BigDecimal secondRead = getAccountBalance(conn1, accountId);
            steps.add("3. Сессия 1: Второе чтение баланса = " + secondRead + " (изменилось!)");

            conn1.commit();

            boolean anomalyDetected = !firstRead.equals(secondRead);

            long duration = System.currentTimeMillis() - startTime;
            log.info(
                    "Non-repeatable Read демонстрация завершена. Аномалия обнаружена: {}. Время"
                            + " выполнения: {} мс",
                    anomalyDetected,
                    duration);

            List<String> recommendations =
                    Arrays.asList(
                            "Используйте уровень изоляции REPEATABLE READ для предотвращения"
                                    + " non-repeatable reads",
                            "Применяйте SELECT FOR UPDATE для блокировки читаемых строк",
                            "Используйте снимки данных (snapshot isolation) для согласованного"
                                    + " чтения");

            return ConcurrencyAnomalyResult.builder()
                    .anomalyType("NON_REPEATABLE_READ")
                    .isolationLevel(isolationLevel)
                    .anomalyDetected(anomalyDetected)
                    .detailedDescription(
                            anomalyDetected
                                    ? "Обнаружен non-repeatable read: Значение баланса изменилось"
                                            + " между чтениями в одной транзакции. Первое чтение: "
                                            + firstRead
                                            + ", второе чтение: "
                                            + secondRead
                                    : "Non-repeatable read не обнаружен")
                    .executionSteps(steps)
                    .initialValue(firstRead.toString())
                    .intermediateValue(secondRead.toString())
                    .finalValue(secondRead.toString())
                    .executionTime(executionTime)
                    .executionDurationMillis(duration)
                    .preventionRecommendations(recommendations)
                    .build();

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка демонстрации non-repeatable read", e);
        }
    }

    @Override
    public ConcurrencyAnomalyResult demonstratePhantomRead(
            Long accountId, BigDecimal thresholdAmount, BigDecimal newTransactionAmount)
            throws DataAccessException {
        log.info(
                "Начало демонстрации Phantom Read для счета {} с порогом {} и новой транзакцией {}",
                accountId,
                thresholdAmount,
                newTransactionAmount);
        LocalDateTime executionTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String isolationLevel = "REPEATABLE READ";
        List<String> steps = new ArrayList<>();

        try (Connection conn1 = getConnection();
                Connection conn2 = getConnection()) {

            // Устанавливаем уровень изоляции ДО начала транзакций
            conn1.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn2.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

            // Сессия 1: Начинает транзакцию и подсчитывает транзакции
            conn1.setAutoCommit(false);
            Integer firstCount = countTransactions(conn1, accountId, thresholdAmount);
            steps.add(
                    "1. Сессия 1 (REPEATABLE READ): Первый подсчет транзакций >= "
                            + thresholdAmount
                            + " = "
                            + firstCount);

            // Сессия 2: Создает новую транзакцию выше порога и коммитит
            conn2.setAutoCommit(false);
            createTransaction(conn2, accountId, newTransactionAmount);
            conn2.commit();
            steps.add(
                    "2. Сессия 2: Создаем транзакцию на сумму "
                            + newTransactionAmount
                            + " и COMMIT");

            // Сессия 1: Подсчитывает транзакции снова
            Integer secondCount = countTransactions(conn1, accountId, thresholdAmount);
            steps.add(
                    "3. Сессия 1: Второй подсчет транзакций = "
                            + secondCount
                            + " (появилась новая запись!)");

            conn1.commit();

            boolean anomalyDetected = !firstCount.equals(secondCount);
            Integer newRecordsCount = secondCount - firstCount;

            long duration = System.currentTimeMillis() - startTime;
            log.info(
                    "Phantom Read демонстрация завершена. Аномалия обнаружена: {}. Новых записей:"
                            + " {}. Время выполнения: {} мс",
                    anomalyDetected,
                    newRecordsCount,
                    duration);

            List<String> recommendations =
                    Arrays.asList(
                            "Используйте уровень изоляции SERIALIZABLE для предотвращения phantom"
                                    + " reads",
                            "Применяйте SELECT FOR UPDATE для блокировки диапазона строк",
                            "Используйте предикатные блокировки (predicate locking) в"
                                    + " SERIALIZABLE");

            return ConcurrencyAnomalyResult.builder()
                    .anomalyType("PHANTOM_READ")
                    .isolationLevel(isolationLevel)
                    .anomalyDetected(anomalyDetected)
                    .detailedDescription(
                            anomalyDetected
                                    ? "Обнаружен phantom read: Появилась новая запись между"
                                            + " повторными чтениями. Первое чтение: "
                                            + firstCount
                                            + " транзакций, второе чтение: "
                                            + secondCount
                                            + " транзакций (+"
                                            + newRecordsCount
                                            + " новых)"
                                    : "Phantom read не обнаружен")
                    .executionSteps(steps)
                    .initialValue(firstCount.toString() + " транзакций")
                    .intermediateValue(secondCount.toString() + " транзакций")
                    .finalValue(secondCount.toString() + " транзакций")
                    .executionTime(executionTime)
                    .executionDurationMillis(duration)
                    .preventionRecommendations(recommendations)
                    .build();

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка демонстрации phantom read", e);
        }
    }

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
    public ConcurrencyAnomalyResult demonstrateLostUpdate(
            Long accountId, BigDecimal firstAmount, BigDecimal secondAmount)
            throws DataAccessException {
        log.info(
                "Начало демонстрации Lost Update для счета {} с суммами {} и {}",
                accountId,
                firstAmount,
                secondAmount);
        LocalDateTime executionTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String isolationLevel = "READ COMMITTED";
        List<String> steps = new ArrayList<>();

        try (Connection conn1 = getConnection();
                Connection conn2 = getConnection()) {

            // Устанавливаем уровень изоляции ДО начала транзакций
            conn1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn2.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            // Получаем начальный баланс
            conn1.setAutoCommit(false);
            BigDecimal initialBalance = getAccountBalance(conn1, accountId);
            steps.add("1. Начальный баланс = " + initialBalance);

            // Сессия 1: Читает баланс
            BigDecimal balance1 = getAccountBalance(conn1, accountId);
            steps.add("2. Сессия 1: Читает баланс = " + balance1);

            // Сессия 2: Читает баланс
            conn2.setAutoCommit(false);
            BigDecimal balance2 = getAccountBalance(conn2, accountId);
            steps.add("3. Сессия 2: Читает баланс = " + balance2);

            // Сессия 1: Обновляет баланс (баланс + firstAmount)
            BigDecimal newBalance1 = balance1.add(firstAmount);
            updateAccountBalance(conn1, accountId, firstAmount);
            conn1.commit();
            steps.add(
                    "4. Сессия 1: Обновляет баланс на "
                            + firstAmount
                            + " и COMMIT. Новый баланс = "
                            + newBalance1);

            // Сессия 2: Обновляет баланс (баланс + secondAmount) - потерянное обновление!
            BigDecimal newBalance2 = balance2.add(secondAmount);
            updateAccountBalance(conn2, accountId, secondAmount);
            conn2.commit();
            steps.add(
                    "5. Сессия 2: Обновляет баланс на "
                            + secondAmount
                            + " и COMMIT. Новый баланс = "
                            + newBalance2);

            // Проверяем финальный баланс
            try (Connection conn3 = getConnection()) {
                BigDecimal finalBalance = getAccountBalance(conn3, accountId);
                steps.add("6. Финальный баланс = " + finalBalance);

                // Ожидаемый баланс = initial + firstAmount + secondAmount
                BigDecimal expectedBalance = initialBalance.add(firstAmount).add(secondAmount);
                boolean anomalyDetected = !finalBalance.equals(expectedBalance);

                long duration = System.currentTimeMillis() - startTime;
                log.info(
                        "Lost Update демонстрация завершена. Аномалия обнаружена: {}. Ожидаемый:"
                                + " {}, Фактический: {}. Время выполнения: {} мс",
                        anomalyDetected,
                        expectedBalance,
                        finalBalance,
                        duration);

                List<String> recommendations =
                        Arrays.asList(
                                "Используйте SELECT FOR UPDATE для блокировки строк перед"
                                        + " обновлением",
                                "Применяйте оптимистичную блокировку через версионирование (version"
                                        + " column)",
                                "Используйте уровень изоляции SERIALIZABLE для предотвращения lost"
                                        + " updates",
                                "Используйте атомарные операции UPDATE с WHERE условиями");

                return ConcurrencyAnomalyResult.builder()
                        .anomalyType("LOST_UPDATE")
                        .isolationLevel(isolationLevel)
                        .anomalyDetected(anomalyDetected)
                        .detailedDescription(
                                anomalyDetected
                                        ? "Обнаружен lost update: Ожидаемый баланс = "
                                                + expectedBalance
                                                + ", фактический баланс = "
                                                + finalBalance
                                                + ". Обновление сессии 1 потеряно!"
                                        : "Lost update не обнаружен")
                        .executionSteps(steps)
                        .initialValue(initialBalance.toString())
                        .intermediateValue(newBalance1.toString())
                        .finalValue(finalBalance.toString())
                        .executionTime(executionTime)
                        .executionDurationMillis(duration)
                        .preventionRecommendations(recommendations)
                        .build();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка демонстрации lost update", e);
        }
    }

    @Override
    public MoneyTransferResult safeMoneyTransfer(
            Long fromAccountId, Long toAccountId, BigDecimal amount) throws DataAccessException {
        log.info(
                "Начало безопасного перевода {} со счета {} на счет {}",
                amount,
                fromAccountId,
                toAccountId);
        LocalDateTime executionTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String isolationLevel = "SERIALIZABLE";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            // Получаем балансы с блокировкой
            BigDecimal fromBalanceBefore = getAccountBalanceWithLock(conn, fromAccountId);
            BigDecimal toBalanceBefore = getAccountBalanceWithLock(conn, toAccountId);

            // Проверяем достаточность средств
            if (fromBalanceBefore.compareTo(amount) < 0) {
                log.warn(
                        "Недостаточно средств на счете {} для перевода {}. Баланс: {}",
                        fromAccountId,
                        amount,
                        fromBalanceBefore);
                conn.rollback();
                return MoneyTransferResult.builder()
                        .status("FAILED")
                        .amount(amount)
                        .fromAccountId(fromAccountId)
                        .toAccountId(toAccountId)
                        .fromAccountBalanceBefore(fromBalanceBefore)
                        .toAccountBalanceBefore(toBalanceBefore)
                        .isolationLevel(isolationLevel)
                        .executionTime(executionTime)
                        .executionDurationMillis(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Выполняем перевод
            updateAccountBalance(conn, fromAccountId, amount.negate());
            updateAccountBalance(conn, toAccountId, amount);

            // Получаем новые балансы
            BigDecimal fromBalanceAfter = getAccountBalance(conn, fromAccountId);
            BigDecimal toBalanceAfter = getAccountBalance(conn, toAccountId);

            // Создаем запись транзакции
            createTransactionRecord(conn, fromAccountId, toAccountId, amount);

            conn.commit();

            long duration = System.currentTimeMillis() - startTime;
            log.info(
                    "Безопасный перевод успешно выполнен за {} мс. Со счета {}: {} -> {}, На счет"
                            + " {}: {} -> {}",
                    duration,
                    fromAccountId,
                    fromBalanceBefore,
                    fromBalanceAfter,
                    toAccountId,
                    toBalanceBefore,
                    toBalanceAfter);

            return MoneyTransferResult.builder()
                    .status("SUCCESS")
                    .amount(amount)
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .fromAccountBalanceBefore(fromBalanceBefore)
                    .fromAccountBalanceAfter(fromBalanceAfter)
                    .toAccountBalanceBefore(toBalanceBefore)
                    .toAccountBalanceAfter(toBalanceAfter)
                    .isolationLevel(isolationLevel)
                    .executionTime(executionTime)
                    .executionDurationMillis(duration)
                    .build();

        } catch (SQLException e) {
            String errorCode = e.getSQLState();
            if ("40001".equals(errorCode)) { // Serialization failure
                log.warn(
                        "Обнаружена ошибка сериализации (40001) при переводе. Повторите операцию.");
                return MoneyTransferResult.builder()
                        .status("SERIALIZATION_FAILURE")
                        .amount(amount)
                        .fromAccountId(fromAccountId)
                        .toAccountId(toAccountId)
                        .isolationLevel(isolationLevel)
                        .executionTime(executionTime)
                        .executionDurationMillis(System.currentTimeMillis() - startTime)
                        .build();
            }
            throw new DataAccessException("Ошибка безопасного перевода денег", e);
        }
    }

    private BigDecimal getAccountBalanceWithLock(Connection conn, Long accountId)
            throws SQLException {
        String sql = "SELECT balance FROM mentee_power.accounts WHERE id = ? FOR UPDATE";
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

    private void createTransactionRecord(
            Connection conn, Long fromAccountId, Long toAccountId, BigDecimal amount)
            throws SQLException {
        // Создаем две записи: списание со счета отправителя и пополнение счета получателя
        String withdrawSql =
                "INSERT INTO mentee_power.transactions (account_id, amount, transaction_type,"
                    + " status, description, created_at) VALUES (?, ?, 'WITHDRAWAL', 'COMPLETED',"
                    + " 'Transfer to account ' || ?, NOW())";
        String depositSql =
                "INSERT INTO mentee_power.transactions (account_id, amount, transaction_type,"
                        + " status, description, created_at) VALUES (?, ?, 'DEPOSIT', 'COMPLETED',"
                        + " 'Transfer from account ' || ?, NOW())";

        try (PreparedStatement withdrawStmt = conn.prepareStatement(withdrawSql);
                PreparedStatement depositStmt = conn.prepareStatement(depositSql)) {
            // Списание со счета отправителя
            withdrawStmt.setLong(1, fromAccountId);
            withdrawStmt.setBigDecimal(2, amount.negate());
            withdrawStmt.setLong(3, toAccountId);
            withdrawStmt.executeUpdate();

            // Пополнение счета получателя
            depositStmt.setLong(1, toAccountId);
            depositStmt.setBigDecimal(2, amount);
            depositStmt.setLong(3, fromAccountId);
            depositStmt.executeUpdate();
        }
    }

    @Override
    public String getCurrentIsolationLevelInfo() throws DataAccessException {
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            StringBuilder info = new StringBuilder();
            info.append("=== PostgreSQL Isolation Level Information ===\n\n");

            // Текущий уровень изоляции
            try (ResultSet rs = stmt.executeQuery("SHOW transaction_isolation")) {
                if (rs.next()) {
                    info.append("Current Transaction Isolation: ")
                            .append(rs.getString(1))
                            .append("\n");
                }
            }

            // Уровень изоляции по умолчанию
            try (ResultSet rs = stmt.executeQuery("SHOW default_transaction_isolation")) {
                if (rs.next()) {
                    info.append("Default Transaction Isolation: ")
                            .append(rs.getString(1))
                            .append("\n");
                }
            }

            // Уровень изоляции сессии
            try (ResultSet rs =
                    stmt.executeQuery("SELECT current_setting('transaction_isolation')")) {
                if (rs.next()) {
                    info.append("Session Transaction Isolation: ")
                            .append(rs.getString(1))
                            .append("\n");
                }
            }

            // Информация о поддержке уровней изоляции
            info.append("\nSupported Isolation Levels:\n");
            info.append("  - READ UNCOMMITTED (PostgreSQL maps to READ COMMITTED)\n");
            info.append("  - READ COMMITTED (default)\n");
            info.append("  - REPEATABLE READ\n");
            info.append("  - SERIALIZABLE\n");

            // Информация о текущей транзакции
            try (ResultSet rs = stmt.executeQuery("SELECT txid_current(), pg_current_xact_id()")) {
                if (rs.next()) {
                    info.append("\nCurrent Transaction ID: ").append(rs.getLong(1)).append("\n");
                }
            }

            return info.toString();

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка получения информации об уровне изоляции", e);
        }
    }
}
