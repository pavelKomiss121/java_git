/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyOrderStats {
    private Integer year;
    private Integer month;
    private Integer ordersCount;
    private BigDecimal monthlyRevenue;
    private BigDecimal avgOrderValue;
}
