/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.interfaces;

import java.sql.Connection;
import java.sql.SQLException;
import ru.mentee.power.connection.model.HealthCheckResult;
import ru.mentee.power.connection.model.PoolStatistics;

/**
 * Менеджер пула соединений с расширенными возможностями.
 */
public interface ConnectionPoolManager {
    /**
     * Получить соединение из пула.
     *
     * @return активное соединение
     * @throws SQLException если не удалось получить соединение
     */
    Connection getConnection() throws SQLException;

    /**
     * Получить статистику использования пула.
     *
     * @return текущая статистика
     */
    PoolStatistics getStatistics();

    /**
     * Выполнить проверку здоровья пула.
     *
     * @return результат проверки
     */
    HealthCheckResult performHealthCheck();

    /**
     * Принудительно обновить соединения в пуле.
     */
    void refreshPool();

    /**
     * Изменить размер пула динамически.
     *
     * @param minSize минимальный размер
     * @param maxSize максимальный размер
     */
    void resizePool(int minSize, int maxSize);

    /**
     * Корректно закрыть пул.
     */
    void shutdown();
}
