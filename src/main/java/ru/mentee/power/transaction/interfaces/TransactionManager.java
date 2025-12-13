/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.interfaces;

import java.sql.Connection;
import java.sql.SQLException;
import ru.mentee.power.transaction.model.ComplexOperationResult;
import ru.mentee.power.transaction.model.IsolationTestReport;
import ru.mentee.power.transaction.model.RetryPolicy;

public interface TransactionManager {
    @FunctionalInterface
    interface TransactionalOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    interface ComplexOperation {
        ComplexOperationResult execute(Connection connection) throws SQLException;
    }

    <T> T executeInTransaction(TransactionalOperation<T> operation, int isolationLevel)
            throws SQLException;

    <T> T executeWithRetry(TransactionalOperation<T> operation, RetryPolicy retryPolicy)
            throws SQLException;

    ComplexOperationResult executeWithSavepoints(ComplexOperation operation) throws SQLException;

    IsolationTestReport testIsolationLevel(int isolationLevel) throws SQLException;
}
