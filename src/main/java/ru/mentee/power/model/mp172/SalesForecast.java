/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp172;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для прогнозирования продаж на основе исторических трендов.
 * Использует регрессионный анализ через оконные функции.
 */
@Data
@Builder
public class SalesForecast {
    /**
     * Дата прогноза.
     */
    private LocalDate forecastDate;

    /**
     * Тип записи: HISTORICAL (исторические данные) или FORECAST (прогноз).
     */
    private String recordType;

    /**
     * Прогнозируемая выручка.
     */
    private BigDecimal forecastedRevenue;

    /**
     * Фактическая выручка (для исторических данных).
     */
    private BigDecimal actualRevenue;

    /**
     * Верхняя граница доверительного интервала (95%).
     */
    private BigDecimal confidenceIntervalUpper;

    /**
     * Нижняя граница доверительного интервала (95%).
     */
    private BigDecimal confidenceIntervalLower;

    /**
     * Прогнозируемое количество заказов.
     */
    private Integer forecastedOrders;

    /**
     * Фактическое количество заказов (для исторических данных).
     */
    private Integer actualOrders;

    /**
     * Тренд выручки (линейный коэффициент).
     */
    private BigDecimal trendCoefficient;

    /**
     * Сезонный коэффициент (для учета сезонности).
     */
    private BigDecimal seasonalCoefficient;

    /**
     * Коэффициент детерминации (R²) модели.
     */
    private BigDecimal rSquared;

    /**
     * Средняя абсолютная процентная ошибка (MAPE).
     */
    private BigDecimal meanAbsolutePercentError;

    /**
     * Стандартная ошибка прогноза.
     */
    private BigDecimal forecastStandardError;

    /**
     * Прогнозируемый средний чек.
     */
    private BigDecimal forecastedAvgOrderValue;

    /**
     * Метод прогнозирования: LINEAR_REGRESSION, MOVING_AVERAGE, EXPONENTIAL_SMOOTHING.
     */
    private String forecastMethod;

    /**
     * Количество месяцев исторических данных, использованных для прогноза.
     */
    private Integer historicalMonths;

    /**
     * Номер месяца прогноза (1 - первый месяц прогноза).
     */
    private Integer forecastMonthNumber;
}
