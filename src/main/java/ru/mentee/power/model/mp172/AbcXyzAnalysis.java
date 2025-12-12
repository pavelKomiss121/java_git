/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp172;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для ABC-XYZ анализа продуктов с квантильным распределением.
 * Использует NTILE и PERCENT_RANK для сложной сегментации.
 */
@Data
@Builder
public class AbcXyzAnalysis {
    /**
     * ID продукта.
     */
    private Long productId;

    /**
     * Название продукта.
     */
    private String productName;

    /**
     * Название категории продукта.
     */
    private String categoryName;

    /**
     * Общая выручка продукта за период.
     */
    private BigDecimal totalRevenue;

    /**
     * Количество проданных единиц.
     */
    private Integer quantitySold;

    /**
     * Средняя цена единицы продукта.
     */
    private BigDecimal avgUnitPrice;

    /**
     * Накопительная выручка (cumulative revenue).
     */
    private BigDecimal cumulativeRevenue;

    /**
     * Накопительный процент от общей выручки.
     */
    private BigDecimal cumulativePercent;

    /**
     * ABC категория по выручке (A, B, C).
     * A - топ 80% выручки, B - следующие 15%, C - остальные 5%.
     */
    private String abcCategory;

    /**
     * Коэффициент вариации продаж (для XYZ анализа).
     */
    private BigDecimal coefficientOfVariation;

    /**
     * Стандартное отклонение продаж.
     */
    private BigDecimal standardDeviation;

    /**
     * Среднее значение продаж.
     */
    private BigDecimal meanSales;

    /**
     * XYZ категория по стабильности продаж (X, Y, Z).
     * X - стабильные (< 0.5), Y - средние (0.5-1.0), Z - нестабильные (> 1.0).
     */
    private String xyzCategory;

    /**
     * Комбинированная ABC-XYZ категория (AX, AY, AZ, BX, BY, BZ, CX, CY, CZ).
     */
    private String abcXyzCategory;

    /**
     * Ранг продукта по выручке.
     */
    private Integer revenueRank;

    /**
     * Ранг продукта по стабильности (1 - самый стабильный).
     */
    private Integer stabilityRank;

    /**
     * Процент ранга (PERCENT_RANK).
     */
    private BigDecimal percentRank;

    /**
     * Квантиль продукта (NTILE).
     */
    private Integer quantile;
}
