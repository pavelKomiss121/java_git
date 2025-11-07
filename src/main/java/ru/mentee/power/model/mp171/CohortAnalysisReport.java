/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp171;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для отчета о когортном анализе.
 * Содержит информацию об удержании клиентов по когортам.
 */
@Data
@Builder
public class CohortAnalysisReport {
    /**
     * Месяц когорты (месяц первой покупки клиентов).
     */
    private LocalDate cohortMonth;

    /**
     * Размер когорты (количество клиентов).
     */
    private Integer cohortSize;

    /**
     * Номер месяца относительно когорты (0 - месяц регистрации).
     */
    private Integer monthNumber;

    /**
     * Количество активных клиентов в этом месяце.
     */
    private Integer activeCustomers;

    /**
     * Уровень удержания (retention rate) в процентах.
     */
    private BigDecimal retentionRate;

    /**
     * Средний чек в этом месяце.
     */
    private BigDecimal avgOrderValue;

    /**
     * Общая выручка когорты.
     */
    private BigDecimal cohortRevenue;
}
