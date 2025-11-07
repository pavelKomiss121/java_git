/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.util.List;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp171.AbcAnalysisReport;
import ru.mentee.power.model.mp171.CategoryHierarchyReport;
import ru.mentee.power.model.mp171.CohortAnalysisReport;
import ru.mentee.power.model.mp171.CustomerSegmentReport;
import ru.mentee.power.model.mp171.ProductTrendReport;

/**
 * Repository для выполнения CTE (Common Table Expression) запросов для аналитики.
 * Предоставляет методы для выполнения различных типов аналитических запросов с использованием CTE.
 */
public interface CteAnalyticsRepository {

    /**
     * Выполняет запрос с множественными CTE для сегментации клиентов.
     * Использует несколько CTE для декомпозиции логики анализа клиентов.
     *
     * @return список отчетов о сегментации клиентов
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<CustomerSegmentReport> executeMultipleCte() throws DataAccessException;

    /**
     * Выполняет рекурсивный CTE запрос для иерархии категорий.
     * Использует рекурсивный CTE для обхода дерева категорий.
     *
     * @return список отчетов об иерархии категорий
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<CategoryHierarchyReport> executeRecursiveCte() throws DataAccessException;

    /**
     * Обрабатывает иерархические данные после выполнения рекурсивного CTE.
     * Выполняет дополнительную обработку результатов иерархического запроса.
     *
     * @return список обработанных отчетов об иерархии категорий
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<CategoryHierarchyReport> processHierarchicalData() throws DataAccessException;

    /**
     * Выполняет когортный анализ с использованием CTE.
     * Анализирует удержание клиентов по месяцам.
     *
     * @return список отчетов о когортном анализе
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<CohortAnalysisReport> performCohortAnalysis() throws DataAccessException;

    /**
     * Выполняет ABC анализ продуктов с использованием CTE.
     * Классифицирует продукты по выручке на категории A, B, C.
     *
     * @return список отчетов ABC анализа
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<AbcAnalysisReport> performAbcAnalysis() throws DataAccessException;

    /**
     * Выполняет анализ трендов продуктов с использованием CTE.
     * Анализирует динамику продаж продуктов по месяцам.
     *
     * @return список отчетов о трендах продуктов
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<ProductTrendReport> performProductTrendsAnalysis() throws DataAccessException;
}
