/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.analytics.*;
import ru.mentee.power.model.mp172.*;

/**
 * Repository для продвинутой аналитики с рекурсивными CTE и сложными оконными функциями.
 * Реализует enterprise-уровень аналитических отчетов.
 */
public interface AdvancedAnalyticsRepository {

    /**
     * Иерархический анализ продаж по организационной структуре.
     * Использует рекурсивные CTE для обхода иерархии организаций.
     *
     * @param includeInactive включать ли неактивные организации
     * @param periodMonths период в месяцах для анализа продаж
     * @return иерархический отчет по продажам организаций
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<OrganizationSalesHierarchy> getOrganizationSalesHierarchy(
            Boolean includeInactive, Integer periodMonths) throws DataAccessException, SQLException;

    /**
     * Анализ временных рядов продаж с трендами и аномалиями.
     * Использует сложные оконные функции для скользящих средних и сравнений.
     *
     * @param startDate начальная дата анализа
     * @param endDate конечная дата анализа
     * @return анализ временных рядов с трендами
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<TimeSeriesAnalysis> getTimeSeriesAnalysis(LocalDate startDate, LocalDate endDate)
            throws DataAccessException;

    /**
     * Продвинутый когортный анализ с прогнозированием LTV.
     * Использует множественные группировки и оконные функции.
     *
     * @param cohortStartMonth начальный месяц когорт для анализа
     * @param maxPeriods максимальное количество периодов для анализа
     * @return многомерный когортный анализ
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<CohortAnalysisAdvanced> getAdvancedCohortAnalysis(
            LocalDate cohortStartMonth, Integer maxPeriods) throws DataAccessException;

    /**
     * ABC-XYZ анализ продуктов с квантильным распределением.
     * Использует NTILE и PERCENT_RANK для сложной сегментации.
     *
     * @param periodDays период в днях для анализа
     * @return ABC-XYZ классификация продуктов
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<AbcXyzAnalysis> getAbcXyzAnalysis(Integer periodDays) throws DataAccessException;

    /**
     * Анализ воронки продаж с конверсиями по этапам.
     * Использует LAG/LEAD для сравнения этапов воронки.
     *
     * @param startDate начальная дата анализа воронки
     * @param endDate конечная дата анализа воронки
     * @return анализ воронки продаж с конверсиями
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<SalesFunnelAnalysis> getSalesFunnelAnalysis(LocalDate startDate, LocalDate endDate)
            throws DataAccessException;

    /**
     * Прогнозирование продаж на основе исторических трендов.
     * Использует регрессионный анализ через оконные функции.
     *
     * @param historicalMonths количество месяцев исторических данных
     * @param forecastMonths количество месяцев для прогноза
     * @return прогноз продаж с доверительными интервалами
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<SalesForecast> getSalesForecast(Integer historicalMonths, Integer forecastMonths)
            throws DataAccessException;
}
