/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.service;

import java.util.List;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.model.mp171.AbcAnalysisReport;
import ru.mentee.power.model.mp171.CategoryHierarchyReport;
import ru.mentee.power.model.mp171.CohortAnalysisReport;
import ru.mentee.power.model.mp171.CustomerSegmentReport;
import ru.mentee.power.model.mp171.ProductTrendReport;

/**
 * Service для работы с CTE (Common Table Expression) аналитикой.
 * Оркестрирует операции с использованием CTE для бизнес-логики аналитики.
 */
public interface CteAnalyticsService {

    /**
     * Получает отчет о сегментации клиентов.
     * Использует множественные CTE для декомпозиции логики анализа клиентов.
     *
     * @return список отчетов о сегментации клиентов
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<CustomerSegmentReport> getCustomerSegmentationReport() throws BusinessException;

    /**
     * Получает отчет о трендах продаж продуктов.
     * Использует CTE с оконными функциями для анализа динамики продаж.
     *
     * @return список отчетов о трендах продуктов
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<ProductTrendReport> getProductTrendsReport() throws BusinessException;

    /**
     * Получает отчет об иерархии категорий.
     * Использует рекурсивный CTE для обхода дерева категорий.
     *
     * @return список отчетов об иерархии категорий
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<CategoryHierarchyReport> getCategoryHierarchyReport() throws BusinessException;

    /**
     * Получает отчет о когортном анализе.
     * Анализирует удержание клиентов по месяцам с использованием CTE.
     *
     * @return список отчетов о когортном анализе
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<CohortAnalysisReport> getCohortAnalysisReport() throws BusinessException;

    /**
     * Получает отчет ABC анализа продуктов.
     * Классифицирует продукты по выручке на категории A, B, C с использованием CTE.
     *
     * @return список отчетов ABC анализа
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<AbcAnalysisReport> getAbcAnalysisReport() throws BusinessException;
}
