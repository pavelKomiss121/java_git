/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp172;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для анализа временных рядов продаж с трендами и аномалиями.
 * Использует сложные оконные функции для скользящих средних и сравнений.
 */
@Data
@Builder
public class TimeSeriesAnalysis {
    /**
     * Дата периода анализа.
     */
    private LocalDate periodDate;

    /**
     * Выручка за период.
     */
    private BigDecimal revenue;

    /**
     * Количество заказов за период.
     */
    private Integer orderCount;

    /**
     * Скользящее среднее выручки (за N периодов).
     */
    private BigDecimal movingAverageRevenue;

    /**
     * Скользящее среднее количества заказов (за N периодов).
     */
    private BigDecimal movingAverageOrders;

    /**
     * Тренд выручки (рост/падение относительно предыдущего периода).
     */
    private BigDecimal revenueTrend;

    /**
     * Процент изменения выручки относительно предыдущего периода.
     */
    private BigDecimal revenueChangePercent;

    /**
     * Процент изменения выручки относительно скользящего среднего.
     */
    private BigDecimal deviationFromAverage;

    /**
     * Флаг аномалии (значительное отклонение от тренда).
     */
    private Boolean isAnomaly;

    /**
     * Тип аномалии (если есть): SPIKE, DROP, SEASONAL, NORMAL.
     */
    private String anomalyType;

    /**
     * Стандартное отклонение от среднего.
     */
    private BigDecimal standardDeviation;

    /**
     * Z-score для определения аномалий.
     */
    private BigDecimal zScore;

    /**
     * Прогнозируемое значение на следующий период.
     */
    private BigDecimal forecastedValue;

    /**
     * Верхняя граница доверительного интервала.
     */
    private BigDecimal confidenceIntervalUpper;

    /**
     * Нижняя граница доверительного интервала.
     */
    private BigDecimal confidenceIntervalLower;
}
