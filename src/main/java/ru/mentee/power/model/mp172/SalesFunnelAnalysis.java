/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp172;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для анализа воронки продаж с конверсиями по этапам.
 * Использует LAG/LEAD для сравнения этапов воронки.
 */
@Data
@Builder
public class SalesFunnelAnalysis {
    /**
     * Название этапа воронки.
     */
    private String funnelStage;

    /**
     * Порядковый номер этапа в воронке.
     */
    private Integer stageOrder;

    /**
     * Количество пользователей/лидов на этапе.
     */
    private Integer usersAtStage;

    /**
     * Количество пользователей, перешедших на следующий этап.
     */
    private Integer usersMovedToNext;

    /**
     * Конверсия с предыдущего этапа (в процентах).
     */
    private BigDecimal conversionFromPrevious;

    /**
     * Конверсия от первого этапа (в процентах).
     */
    private BigDecimal conversionFromFirst;

    /**
     * Количество оттока на этапе (не перешли дальше).
     */
    private Integer dropouts;

    /**
     * Процент оттока на этапе.
     */
    private BigDecimal dropoutRate;

    /**
     * Среднее время на этапе (в днях).
     */
    private BigDecimal avgTimeOnStage;

    /**
     * Выручка, сгенерированная на этапе.
     */
    private BigDecimal stageRevenue;

    /**
     * Средняя выручка на пользователя этапа.
     */
    private BigDecimal avgRevenuePerUser;

    /**
     * Количество завершенных сделок на этапе.
     */
    private Integer completedDeals;

    /**
     * Конверсия в завершенные сделки (в процентах).
     */
    private BigDecimal dealConversionRate;

    /**
     * Начальная дата анализа воронки.
     */
    private LocalDate analysisStartDate;

    /**
     * Конечная дата анализа воронки.
     */
    private LocalDate analysisEndDate;

    /**
     * Изменение конверсии относительно предыдущего периода.
     */
    private BigDecimal conversionChangePercent;

    /**
     * Тренд этапа (улучшение/ухудшение).
     */
    private String stageTrend;
}
