/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp172;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для иерархического анализа продаж по организационной структуре.
 * Использует рекурсивные CTE для обхода иерархии организаций.
 */
@Data
@Builder
public class OrganizationSalesHierarchy {
    /**
     * ID организации.
     */
    private Long organizationId;

    /**
     * Название организации.
     */
    private String organizationName;

    /**
     * ID родительской организации (null для корневых).
     */
    private Long parentOrganizationId;

    /**
     * Уровень в иерархии (0 - корневой уровень).
     */
    private Integer hierarchyLevel;

    /**
     * Путь в иерархии (например, "Root > Division > Department").
     */
    private String hierarchyPath;

    /**
     * Общая выручка организации за период.
     */
    private BigDecimal totalRevenue;

    /**
     * Выручка организации (без учета дочерних).
     */
    private BigDecimal ownRevenue;

    /**
     * Выручка всех дочерних организаций.
     */
    private BigDecimal childrenRevenue;

    /**
     * Количество заказов организации.
     */
    private Integer orderCount;

    /**
     * Средний чек организации.
     */
    private BigDecimal avgOrderValue;

    /**
     * Процент от общей выручки всей иерархии.
     */
    private BigDecimal revenuePercent;

    /**
     * Период анализа (начальная дата).
     */
    private LocalDate periodStart;

    /**
     * Период анализа (конечная дата).
     */
    private LocalDate periodEnd;

    /**
     * Флаг активности организации.
     */
    private Boolean isActive;
}
