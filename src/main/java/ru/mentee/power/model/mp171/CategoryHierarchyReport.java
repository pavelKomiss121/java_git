/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp171;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для отчета об иерархии категорий.
 * Содержит информацию о категориях с учетом их иерархической структуры.
 */
@Data
@Builder
public class CategoryHierarchyReport {
    /**
     * Имя категории с отступами для визуализации иерархии.
     */
    private String indentedName;

    /**
     * Полный путь категории (например, "Electronics > Laptops").
     */
    private String fullPath;

    /**
     * Уровень в иерархии (0 - корневая категория).
     */
    private Integer level;

    /**
     * Количество продуктов в категории.
     */
    private Integer productsCount;

    /**
     * Количество заказов с продуктами из категории.
     */
    private Integer ordersCount;

    /**
     * Общая выручка категории.
     */
    private BigDecimal totalRevenue;

    /**
     * Выручка корневой категории (для расчета доли).
     */
    private BigDecimal rootCategoryRevenue;
}
