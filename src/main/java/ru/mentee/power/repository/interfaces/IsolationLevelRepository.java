/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.util.Map;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp166.OperationResult;
import ru.mentee.power.model.mp166.TransactionContext;
import ru.mentee.power.model.mp166.TransactionOperation;

/**
 * Repository для выполнения операций с различными уровнями изоляции транзакций.
 */
public interface IsolationLevelRepository {

    /**
     * Выполняет операцию с заданным уровнем изоляции.
     */
    <T> T executeWithIsolationLevel(String isolationLevel, TransactionOperation<T> operation)
            throws DataAccessException;

    /**
     * Начинает транзакцию с указанным уровнем изоляции.
     */
    TransactionContext startTransactionWithLevel(String isolationLevel);

    /**
     * Выполняет конкурентную операцию для тестирования race conditions.
     */
    OperationResult performConcurrentOperation(
            TransactionContext context, String operation, Map<String, Object> params);
}
