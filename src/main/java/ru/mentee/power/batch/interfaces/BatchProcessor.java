/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.interfaces;

import java.sql.SQLException;
import java.util.List;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.BatchResult;
import ru.mentee.power.batch.model.DetailedBatchResult;

/**
 * Процессор для выполнения batch операций с БД.
 */
public interface BatchProcessor {
    /**
     * Выполнить batch вставку записей.
     *
     * @param records записи для вставки
     * @return результат операции
     * @throws SQLException при ошибках БД
     */
    <T> BatchResult insert(List<T> records) throws SQLException;

    /**
     * Выполнить batch обновление записей.
     *
     * @param records записи для обновления
     * @return результат операции
     * @throws SQLException при ошибках БД
     */
    <T> BatchResult update(List<T> records) throws SQLException;

    /**
     * Выполнить batch удаление записей.
     *
     * @param ids идентификаторы для удаления
     * @return результат операции
     * @throws SQLException при ошибках БД
     */
    BatchResult delete(List<Long> ids) throws SQLException;

    /**
     * Выполнить upsert (INSERT ON CONFLICT UPDATE).
     *
     * @param records записи для upsert
     * @return результат операции
     * @throws SQLException при ошибках БД
     */
    <T> BatchResult upsert(List<T> records) throws SQLException;

    /**
     * Выполнить batch операцию с детальной обработкой ошибок.
     *
     * @param records записи для обработки
     * @param operation тип операции
     * @return детальный результат с информацией об ошибках
     * @throws SQLException при критических ошибках
     */
    <T> DetailedBatchResult processWithDetails(List<T> records, BatchOperation operation)
            throws SQLException;
}
