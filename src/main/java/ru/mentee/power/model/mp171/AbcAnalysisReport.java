/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp171;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * DTO для отчета ABC-анализа продуктов.
 * Содержит информацию о классификации продуктов по продажам.
 */
@Data
@Builder
public class AbcAnalysisReport {
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
     * Общая выручка продукта.
     */
    private BigDecimal totalRevenue;

    /**
     * Накопительная выручка (cumulative revenue).
     */
    private BigDecimal cumulativeRevenue;

    /**
     * Накопительный процент от общей выручки.
     */
    private BigDecimal cumulativePercent;

    /**
     * ABC категория (A, B, C).
     */
    private String abcCategory;

    /**
     * Ранг продукта по выручке.
     */
    private Integer revenueRank;
}
