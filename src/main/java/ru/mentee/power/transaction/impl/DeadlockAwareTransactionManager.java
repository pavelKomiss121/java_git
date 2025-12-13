/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.impl;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.transaction.interfaces.TransactionManager;
import ru.mentee.power.transaction.model.RetryPolicy;

@Slf4j
public class DeadlockAwareTransactionManager {
    private final DataSource dataSource;

    public DeadlockAwareTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T executeWithDeadlockRetry(
            TransactionManager.TransactionalOperation<T> operation, RetryPolicy retryPolicy)
            throws SQLException {
        int attempts = 0;
        SQLException lastException = null;

        while (attempts < retryPolicy.getMaxAttempts()) {
            attempts++;
            try {
                return executeInTransaction(operation);
            } catch (SQLException e) {
                if (isDeadlock(e)) {
                    lastException = e;
                    log.warn(
                            "Обнаружен дедлок, попытка {} из {}",
                            attempts,
                            retryPolicy.getMaxAttempts());
                    if (attempts < retryPolicy.getMaxAttempts()) {
                        long delay = retryPolicy.calculateDelay(attempts);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new SQLException("Прервано ожидание retry", ie);
                        }
                    }
                } else {
                    throw e;
                }
            }
        }

        throw new SQLException(
                "Не удалось выполнить операцию после "
                        + retryPolicy.getMaxAttempts()
                        + " попыток из-за дедлоков",
                lastException);
    }

    private <T> T executeInTransaction(TransactionManager.TransactionalOperation<T> operation)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                T result = operation.execute(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private boolean isDeadlock(SQLException e) {
        if ("40P01".equals(e.getSQLState())) {
            return true;
        }
        if ("40001".equals(e.getSQLState())) {
            return true;
        }
        String message = e.getMessage().toLowerCase();
        return message.contains("deadlock") || message.contains("lock timeout");
    }
}
