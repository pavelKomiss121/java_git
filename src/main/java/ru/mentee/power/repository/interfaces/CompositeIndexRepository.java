/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp163.IndexUsageStats;
import ru.mentee.power.model.mp163.OrderAnalytics;
import ru.mentee.power.model.mp163.PerformanceMetrics;

/**
 * Repository для демонстрации составных и функциональных индексов.
 * Показывает оптимизацию сложных аналитических запросов.
 */
public interface CompositeIndexRepository {

    /**
     * Выполняет аналитику заказов по региону, статусу и периоду БЕЗ составного индекса.
     *
     * @param regions список регионов для анализа
     * @param statuses список статусов заказов
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     * @return результаты аналитики с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    PerformanceMetrics<List<OrderAnalytics>> getOrderAnalyticsWithoutIndex(
            List<String> regions, List<String> statuses, LocalDate startDate, LocalDate endDate)
            throws DataAccessException;

    /**
     * Выполняет аналитику заказов С составным индексом (region, status, created_at).
     *
     * @param regions список регионов для анализа
     * @param statuses список статусов заказов
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     * @return результаты аналитики с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    PerformanceMetrics<List<OrderAnalytics>> getOrderAnalyticsWithIndex(
            List<String> regions, List<String> statuses, LocalDate startDate, LocalDate endDate)
            throws DataAccessException;

    /**
     * Тестирует производительность поиска по различным колонкам без индекса.
     *
     * @param categoryId идентификатор категории
     * @param minPrice минимальная цена
     * @param maxPrice максимальная цена
     * @return метрики производительности запроса
     * @throws DataAccessException при ошибках доступа к данным
     */
    PerformanceMetrics<Long> measureQueryWithoutIndex(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) throws DataAccessException;

    /**
     * Тестирует производительность поиска по тем же колонкам с составным индексом.
     *
     * @param categoryId идентификатор категории
     * @param minPrice минимальная цена
     * @param maxPrice максимальная цена
     * @return метрики производительности запроса
     * @throws DataAccessException при ошибках доступа к данным
     */
    PerformanceMetrics<Long> measureQueryWithIndex(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) throws DataAccessException;

    /**
     * Создает составные и функциональные индексы для оптимизации.
     *
     * @return результат создания индексов с временными метриками
     * @throws DataAccessException при ошибках создания индексов
     */
    PerformanceMetrics<String> createCompositeIndexes() throws DataAccessException;

    /**
     * Удаляет составные и функциональные индексы.
     *
     * @return результат удаления индексов
     * @throws DataAccessException при ошибках удаления индексов
     */
    PerformanceMetrics<String> dropCompositeIndexes() throws DataAccessException;

    /**
     * Анализирует эффективность составных индексов.
     *
     * @return статистика использования индексов
     * @throws DataAccessException при ошибках запроса статистики
     */
    List<IndexUsageStats> analyzeCompositeIndexUsage() throws DataAccessException;
}
