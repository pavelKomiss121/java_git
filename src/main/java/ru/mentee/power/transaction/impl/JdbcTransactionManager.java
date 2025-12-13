/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.impl;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.transaction.interfaces.TransactionManager;
import ru.mentee.power.transaction.model.ComplexOperationResult;
import ru.mentee.power.transaction.model.IsolationTestReport;
import ru.mentee.power.transaction.model.RetryPolicy;

@Slf4j
public class JdbcTransactionManager implements TransactionManager {
    private final DataSource dataSource;

    public JdbcTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T executeInTransaction(TransactionalOperation<T> operation, int isolationLevel)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            int originalLevel = connection.getTransactionIsolation();
            boolean autoCommit = connection.getAutoCommit();
            try {
                connection.setTransactionIsolation(isolationLevel);
                connection.setAutoCommit(false);
                T result = operation.execute(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setTransactionIsolation(originalLevel);
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public <T> T executeWithRetry(TransactionalOperation<T> operation, RetryPolicy retryPolicy)
            throws SQLException {
        DeadlockAwareTransactionManager deadlockManager =
                new DeadlockAwareTransactionManager(dataSource);
        return deadlockManager.executeWithDeadlockRetry(operation, retryPolicy);
    }

    @Override
    public ComplexOperationResult executeWithSavepoints(ComplexOperation operation)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return operation.execute(connection);
        }
    }

    @Override
    public IsolationTestReport testIsolationLevel(int isolationLevel) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            IsolationLevelManager isolationManager = new IsolationLevelManager(connection);
            return isolationManager.testIsolationLevel(isolationLevel);
        }
    }
}
