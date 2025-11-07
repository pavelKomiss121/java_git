/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp171;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для отчета о трендах продаж продуктов.
 * Содержит информацию о динамике продаж продуктов по месяцам.
 */
@Data
@Builder
public class ProductTrendReport {
    /**
     * Название продукта.
     */
    private String productName;

    /**
     * Название категории продукта.
     */
    private String categoryName;

    /**
     * Месяц продаж.
     */
    private LocalDate salesMonth;

    /**
     * Выручка за месяц.
     */
    private BigDecimal revenue;

    /**
     * Количество проданных единиц.
     */
    private Integer unitsSold;

    /**
     * Процент роста выручки по сравнению с предыдущим месяцем.
     */
    private BigDecimal revenueGrowthPercent;

    /**
     * Процент роста количества проданных единиц.
     */
    private BigDecimal unitsGrowthPercent;

    /**
     * Категория тренда (GROWING_FAST, GROWING, STABLE, DECLINING).
     */
    private String trendCategory;
}
