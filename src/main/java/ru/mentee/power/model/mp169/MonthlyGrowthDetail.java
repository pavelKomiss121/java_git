/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonthlyGrowthDetail {
    private LocalDateTime month;
    private BigDecimal sales;
    private BigDecimal previousMonthSales;
    private BigDecimal monthOverMonthGrowth;
    private Integer orderCount;
}
