/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryGrowthAnalytics {
    private Long categoryId;
    private String categoryName;
    private BigDecimal totalSales;
    private BigDecimal previousPeriodSales;
    private BigDecimal growthPercentage;
    private BigDecimal growthAmount;
    private Integer orderCount;
    private Integer previousPeriodOrderCount;
    private LocalDateTime analysisStartDate;
    private LocalDateTime analysisEndDate;
    private List<MonthlyGrowthDetail> monthlyDetails;
    private String growthTrend;
    private Integer rank;
}
