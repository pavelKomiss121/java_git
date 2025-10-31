/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.time.LocalDate;
import java.util.List;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.analytics.PerformanceMetrics;
import ru.mentee.power.model.analytics.QueryExecutionPlan;
import ru.mentee.power.model.analytics.UserOrderStats;

/**
 * Repository для демонстрации анализа производительности запросов.
 * Показывает разницу между медленными и оптимизированными запросами через JDBC.
 */
public interface PerformanceAnalysisRepository {

    /**
     * Выполняет медленный запрос БЕЗ индексов для демонстрации проблем.
     *
     * @param city город для фильтрации пользователей
     * @param startDate начальная дата заказов
     * @param minOrders минимальное количество заказов
     * @return статистика пользователей с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    PerformanceMetrics<List<UserOrderStats>> getSlowUserOrderStats(
            String city, LocalDate startDate, Integer minOrders) throws DataAccessException;

    /**
     * Выполняет тот же запрос С индексами для демонстрации улучшений.
     *
     * @param city город для фильтрации пользователей
     * @param startDate начальная дата заказов
     * @param minOrders минимальное количество заказов
     * @return статистика пользователей с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    PerformanceMetrics<List<UserOrderStats>> getFastUserOrderStats(
            String city, LocalDate startDate, Integer minOrders) throws DataAccessException;

    /**
     * Получает план выполнения для конкретного запроса.
     *
     * @param query SQL запрос для анализа
     * @return детальный план выполнения с EXPLAIN ANALYZE
     * @throws DataAccessException при ошибках выполнения
     */
    QueryExecutionPlan getExecutionPlan(String query) throws DataAccessException;

    /**
     * Создает необходимые индексы для оптимизации.
     *
     * @return результат создания индексов с временными метриками
     * @throws DataAccessException при ошибках создания индексов
     */
    PerformanceMetrics<String> createOptimizationIndexes() throws DataAccessException;

    /**
     * Удаляет индексы для демонстрации деградации производительности.
     *
     * @return результат удаления индексов
     * @throws DataAccessException при ошибках удаления индексов
     */
    PerformanceMetrics<String> dropOptimizationIndexes() throws DataAccessException;
}
