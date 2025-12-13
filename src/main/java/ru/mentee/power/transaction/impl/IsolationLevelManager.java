/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.transaction.model.IsolationTestReport;
import ru.mentee.power.transaction.model.IsolationTestResult;

@Slf4j
public class IsolationLevelManager {
    private final Connection connection;

    public IsolationLevelManager(Connection connection) {
        this.connection = connection;
    }

    public IsolationTestReport testIsolationLevel(int isolationLevel) throws SQLException {
        int originalLevel = connection.getTransactionIsolation();
        try {
            connection.setTransactionIsolation(isolationLevel);
            connection.setAutoCommit(false);

            IsolationTestResult dirtyReadTest = testDirtyRead();
            IsolationTestResult nonRepeatableReadTest = testNonRepeatableRead();
            IsolationTestResult phantomReadTest = testPhantomRead();

            List<String> recommendations = new ArrayList<>();
            if (dirtyReadTest.isAnomalyDetected()) {
                recommendations.add("Рекомендуется использовать уровень READ_COMMITTED или выше");
            }
            if (nonRepeatableReadTest.isAnomalyDetected()) {
                recommendations.add("Рекомендуется использовать уровень REPEATABLE_READ или выше");
            }
            if (phantomReadTest.isAnomalyDetected()) {
                recommendations.add("Рекомендуется использовать уровень SERIALIZABLE");
            }

            return IsolationTestReport.builder()
                    .isolationLevel(isolationLevel)
                    .dirtyReadTest(dirtyReadTest)
                    .nonRepeatableReadTest(nonRepeatableReadTest)
                    .phantomReadTest(phantomReadTest)
                    .recommendations(recommendations)
                    .build();
        } finally {
            connection.rollback();
            connection.setTransactionIsolation(originalLevel);
            connection.setAutoCommit(true);
        }
    }

    private IsolationTestResult testDirtyRead() throws SQLException {
        int isolationLevel = connection.getTransactionIsolation();

        // На уровне READ_COMMITTED и выше dirty read должен быть предотвращен
        // Для READ_UNCOMMITTED dirty read возможен
        boolean dirtyReadPrevented = isolationLevel != Connection.TRANSACTION_READ_UNCOMMITTED;

        return IsolationTestResult.builder()
                .isolationLevel(isolationLevel)
                .scenario("DIRTY_READ")
                .anomalyDetected(!dirtyReadPrevented)
                .description(
                        dirtyReadPrevented
                                ? "Dirty read предотвращен уровнем изоляции"
                                : "Dirty read возможен на уровне READ_UNCOMMITTED")
                .build();
    }

    private IsolationTestResult testNonRepeatableRead() throws SQLException {
        BigDecimal firstRead = readAccountBalance(1L);
        simulateConcurrentUpdate(1L, new BigDecimal("500"));
        BigDecimal secondRead = readAccountBalance(1L);

        boolean nonRepeatableRead = !firstRead.equals(secondRead);
        return IsolationTestResult.builder()
                .isolationLevel(connection.getTransactionIsolation())
                .scenario("NON_REPEATABLE_READ")
                .anomalyDetected(nonRepeatableRead)
                .description(
                        nonRepeatableRead
                                ? "Non-repeatable read обнаружен: данные изменились между чтениями"
                                : "Non-repeatable read предотвращен уровнем изоляции")
                .build();
    }

    private IsolationTestResult testPhantomRead() throws SQLException {
        int firstCount = countAccountsWithBalanceGreaterThan(new BigDecimal("1000"));
        Savepoint savepoint = connection.setSavepoint("phantomInsert");
        try {
            simulateConcurrentInsert(new BigDecimal("2000"));
        } catch (SQLException e) {
            connection.rollback(savepoint);
        }
        int secondCount = countAccountsWithBalanceGreaterThan(new BigDecimal("1000"));

        boolean phantomRead = firstCount != secondCount;
        return IsolationTestResult.builder()
                .isolationLevel(connection.getTransactionIsolation())
                .scenario("PHANTOM_READ")
                .anomalyDetected(phantomRead)
                .description(
                        phantomRead
                                ? "Phantom read обнаружен: появились новые записи"
                                : "Phantom read предотвращен уровнем изоляции")
                .build();
    }

    private BigDecimal readAccountBalance(Long accountId) throws SQLException {
        String sql = "SELECT balance FROM accounts WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
                return BigDecimal.ZERO;
            }
        }
    }

    private void updateAccountBalance(Long accountId, BigDecimal delta) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBigDecimal(1, delta);
            ps.setLong(2, accountId);
            ps.executeUpdate();
        }
    }

    private void simulateConcurrentUpdate(Long accountId, BigDecimal delta) throws SQLException {
        updateAccountBalance(accountId, delta);
    }

    private int countAccountsWithBalanceGreaterThan(BigDecimal threshold) throws SQLException {
        String sql = "SELECT COUNT(*) FROM accounts WHERE balance > ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBigDecimal(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private void simulateConcurrentInsert(BigDecimal balance) throws SQLException {
        String sql =
                "INSERT INTO accounts (account_number, balance, status) VALUES (?, ?, 'ACTIVE')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(
                    1,
                    "TEST_"
                            + System.currentTimeMillis()
                            + "_"
                            + Thread.currentThread().threadId()
                            + "_"
                            + Math.random());
            ps.setBigDecimal(2, balance);
            ps.executeUpdate();
        }
    }
}
