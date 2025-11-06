/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.service;

import java.util.List;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.model.mp170.DailySalesReport;
import ru.mentee.power.model.mp170.SalesPersonRanking;

/**
 * Service для работы с оконными функциями в аналитике продаж.
 * Оркестрирует операции с использованием Window Functions для бизнес-логики.
 */
public interface WindowFunctionsService {

    /**
     * Получает ранжированных продавцов по регионам.
     * Использует RANK, DENSE_RANK и ROW_NUMBER для различных типов ранжирования.
     *
     * @return список продавцов с рангами и долей рынка
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<SalesPersonRanking> getRankedSalesPeopleByRegion() throws BusinessException;

    /**
     * Получает ежедневные продажи с накопительными суммами.
     * Использует SUM() OVER (ORDER BY) для расчета running totals.
     *
     * @return список ежедневных отчетов с накопительными суммами и метриками роста
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<DailySalesReport> getDailySalesWithRunningTotals() throws BusinessException;

    /**
     * Сравнивает производительность продавцов с региональным средним.
     * Использует AVG() OVER (PARTITION BY) для расчета региональных средних.
     *
     * @return список продавцов с отклонениями от регионального среднего
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<SalesPersonRanking> getPerformanceVsRegionalAverage() throws BusinessException;

    /**
     * Получает топ продуктов с долей рынка.
     * Использует оконные функции для расчета market share.
     *
     * @param topLimit количество топ продуктов
     * @return список продуктов с долей рынка
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<SalesPersonRanking> getTopProductsWithMarketShare(Integer topLimit)
            throws BusinessException;

    /**
     * Получает распределение продавцов по квартилям.
     * Использует NTILE() для разделения на 4 группы по производительности.
     *
     * @param quartileNumber номер квартиля (1-4), где 1 - лучшие, 4 - худшие
     * @return список продавцов в указанном квартиле
     * @throws BusinessException при ошибках бизнес-логики
     */
    List<SalesPersonRanking> getSalesPersonQuartiles(Integer quartileNumber)
            throws BusinessException;
}
