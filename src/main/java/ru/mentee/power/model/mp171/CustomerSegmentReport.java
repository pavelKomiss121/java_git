/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp171;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для отчета о сегментации клиентов.
 * Содержит информацию о сегментах клиентов, их активности и финансовых показателях.
 */
@Data
@Builder
public class CustomerSegmentReport {
    /**
     * Название сегмента клиента (VIP, PREMIUM, REGULAR, NEW, INACTIVE).
     */
    private String segment;

    /**
     * Статус активности клиента (ACTIVE, AT_RISK, CHURNED).
     */
    private String activityStatus;

    /**
     * Количество клиентов в сегменте.
     */
    private Integer customersCount;

    /**
     * Средние траты клиентов в сегменте.
     */
    private BigDecimal avgTotalSpent;

    /**
     * Средний чек клиентов в сегменте.
     */
    private BigDecimal avgOrderValue;

    /**
     * Общая выручка сегмента.
     */
    private BigDecimal segmentRevenue;

    /**
     * Доля выручки сегмента в общей выручке (в процентах).
     */
    private BigDecimal revenueSharePercent;
}
