/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp172;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для продвинутого когортного анализа с прогнозированием LTV.
 * Использует множественные группировки и оконные функции.
 */
@Data
@Builder
public class CohortAnalysisAdvanced {
    /**
     * Месяц когорты (месяц первой покупки клиентов).
     */
    private LocalDate cohortMonth;

    /**
     * Размер когорты (количество клиентов в когорте).
     */
    private Integer cohortSize;

    /**
     * Номер периода относительно когорты (0 - месяц регистрации).
     */
    private Integer periodNumber;

    /**
     * Дата периода анализа.
     */
    private LocalDate periodDate;

    /**
     * Количество активных клиентов в этом периоде.
     */
    private Integer activeCustomers;

    /**
     * Уровень удержания (retention rate) в процентах.
     */
    private BigDecimal retentionRate;

    /**
     * Выручка когорты в этом периоде.
     */
    private BigDecimal periodRevenue;

    /**
     * Накопительная выручка когорты (с начала).
     */
    private BigDecimal cumulativeRevenue;

    /**
     * Средний чек в этом периоде.
     */
    private BigDecimal avgOrderValue;

    /**
     * Среднее количество заказов на клиента в периоде.
     */
    private BigDecimal avgOrdersPerCustomer;

    /**
     * Прогнозируемый LTV (Lifetime Value) клиента когорты.
     */
    private BigDecimal predictedLtv;

    /**
     * Реализованный LTV на текущий момент.
     */
    private BigDecimal realizedLtv;

    /**
     * Прогнозируемый LTV с учетом тренда удержания.
     */
    private BigDecimal projectedLtv;

    /**
     * Коэффициент конверсии (процент клиентов, сделавших покупку).
     */
    private BigDecimal conversionRate;

    /**
     * Средний интервал между покупками (в днях).
     */
    private BigDecimal avgDaysBetweenOrders;

    /**
     * Процент оттока клиентов (churn rate).
     */
    private BigDecimal churnRate;

    /**
     * Прогнозируемый срок жизни клиента (в месяцах).
     */
    private BigDecimal predictedLifetimeMonths;
}
