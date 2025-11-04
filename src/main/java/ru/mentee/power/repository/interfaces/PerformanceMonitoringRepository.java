/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.util.List;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp164.*;

/**
 * Repository для мониторинга производительности БД и анализа индексов.
 * Предоставляет статистику использования индексов и планы выполнения.
 */
public interface PerformanceMonitoringRepository {

    /**
     * Анализ статистики использования всех индексов.
     * Показывает эффективность и частоту использования.
     */
    List<IndexUsageStats> getIndexUsageStatistics() throws DataAccessException;

    /**
     * Поиск неиспользуемых индексов для удаления.
     * Помогает оптимизировать дисковое пространство.
     */
    List<UnusedIndexReport> getUnusedIndexes() throws DataAccessException;

    /**
     * Анализ медленных запросов с рекомендациями по индексам.
     * Использует pg_stat_statements для выявления проблем.
     */
    List<SlowQueryReport> getSlowQueriesWithRecommendations() throws DataAccessException;

    /**
     * Получение плана выполнения для конкретного запроса.
     * Анализирует EXPLAIN ANALYZE результаты.
     */
    QueryExecutionPlan getQueryExecutionPlan(String query) throws DataAccessException;

    /**
     * Статистика по таблицам: размер, количество строк, эффективность индексов.
     * Помогает понять общее состояние БД.
     */
    List<TableStatistics> getTableStatistics() throws DataAccessException;

    /**
     * Мониторинг активности БД в реальном времени.
     * Показывает текущие выполняющиеся запросы.
     */
    List<ActiveQueryInfo> getCurrentActiveQueries() throws DataAccessException;

    /**
     * Анализ кэш hit ratio для оптимизации памяти.
     * Показывает эффективность использования shared_buffers.
     */
    CacheHitRatioReport getCacheHitRatioReport() throws DataAccessException;

    /**
     * Создание индекса с анализом его влияния на производительность.
     * Безопасное создание с мониторингом времени.
     */
    IndexCreationResult createIndexSafely(
            String indexName, String tableName, String columns, String indexType)
            throws DataAccessException;
}
