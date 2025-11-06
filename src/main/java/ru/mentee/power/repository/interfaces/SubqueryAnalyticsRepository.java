/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp169.*;

/**
 * Repository для демонстрации подзапросов и UNION операций.
 * Показывает сложные аналитические запросы для бизнес-задач.
 */
public interface SubqueryAnalyticsRepository {

    /**
     * Находит VIP клиентов используя скалярные подзапросы.
     * Клиенты с тратами выше среднего по всем клиентам.
     *
     * @param minSpendingMultiplier множитель среднего (например, 1.5 = на 50% больше среднего)
     * @param limit максимальное количество результатов
     * @return список VIP клиентов с аналитикой
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<VipCustomerAnalytics> findVipCustomersWithSubqueries(
            Double minSpendingMultiplier, Integer limit) throws DataAccessException, SQLException;

    /**
     * Ищет товары без продаж используя EXISTS подзапросы.
     * Помогает выявить неликвидные товары для принятия решений.
     *
     * @param daysSinceLastSale количество дней без продаж
     * @param includeNewProducts включать ли новые товары (созданные недавно)
     * @return список товаров без продаж
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<UnsoldProductAnalytics> findUnsoldProductsWithExists(
            Integer daysSinceLastSale, Boolean includeNewProducts)
            throws DataAccessException, SQLException;

    /**
     * Анализирует аномальные заказы используя коррелированные подзапросы.
     * Находит заказы, которые существенно отличаются от паттерна пользователя.
     *
     * @param anomalyThresholdPercent порог аномальности в процентах (например, 200 = в 2 раза больше среднего)
     * @param analysisMonths количество месяцев для анализа
     * @return список аномальных заказов с объяснениями
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<AnomalousOrderAnalytics> findAnomalousOrdersWithCorrelatedSubqueries(
            Double anomalyThresholdPercent, Integer analysisMonths) throws DataAccessException;

    /**
     * Создает полную историю активности пользователя используя UNION ALL.
     * Объединяет заказы, отзывы, использование промокодов.
     *
     * @param userId идентификатор пользователя
     * @param fromDate начальная дата для анализа
     * @param limit максимальное количество записей
     * @return хронологическая история активности
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<UserActivitySummary> getUserActivityHistoryWithUnion(
            Long userId, LocalDate fromDate, Integer limit) throws DataAccessException;

    /**
     * Создает сводный отчет по продажам используя UNION.
     * Объединяет данные по брендам, категориям и источникам заказов.
     *
     * @param reportStartDate начальная дата отчета
     * @param reportEndDate конечная дата отчета
     * @param minSalesThreshold минимальный порог продаж для включения в отчет
     * @return сводный отчет по разным измерениям
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<SalesReportDimension> getSalesReportWithUnion(
            LocalDate reportStartDate, LocalDate reportEndDate, BigDecimal minSalesThreshold)
            throws DataAccessException;

    /**
     * Сравнивает производительность подзапросов vs JOIN.
     * Выполняет одинаковую бизнес-логику разными способами для анализа.
     *
     * @param customerTier уровень клиента для анализа
     * @param monthsBack количество месяцев назад для анализа
     * @return результаты сравнения производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    SubqueryPerformanceComparison compareSubqueryVsJoinPerformance(
            String customerTier, Integer monthsBack) throws DataAccessException;

    /**
     * Выполняет сложный многоуровневый анализ используя вложенные подзапросы.
     * Находит топ категории по росту продаж с детализацией по месяцам.
     *
     * @param analysisMonths количество месяцев для анализа тренда
     * @param topCategoriesLimit количество топ категорий
     * @return анализ трендов роста по категориям
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<CategoryGrowthAnalytics> analyzeTopGrowingCategoriesWithNestedSubqueries(
            Integer analysisMonths, Integer topCategoriesLimit) throws DataAccessException;

    /**
     * Находит потенциальных клиентов для upselling используя сложные подзапросы.
     * Анализирует паттерны покупок для персонализированных рекомендаций.
     *
     * @param targetCustomerTier целевой уровень клиента
     * @param recommendationLimit количество рекомендаций
     * @return список клиентов с рекомендациями для upselling
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<UpsellingOpportunity> findUpsellingOpportunitiesWithSubqueries(
            String targetCustomerTier, Integer recommendationLimit) throws DataAccessException;
}
