/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Функциональный интерфейс для выполнения операции в рамках транзакции с заданным уровнем изоляции.
 *
 * @param <T> тип результата операции
 */
@FunctionalInterface
public interface TransactionOperation<T> {
    /**
     * Выполняет операцию внутри транзакции.
     *
     * @param connection активное соединение с БД внутри транзакции
     * @return результат операции
     * @throws SQLException если произошла ошибка при выполнении SQL
     */
    T execute(Connection connection) throws SQLException;
}
