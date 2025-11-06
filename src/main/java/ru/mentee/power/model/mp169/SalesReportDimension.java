/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SalesReportDimension {
    private String dimensionName;
    private String dimensionType;
    private BigDecimal totalSales;
    private BigDecimal growthPercentage;
    private Integer orderCount;
    private Integer itemCount;
    private BigDecimal averageOrderValue;
    private String performanceRating;
}
