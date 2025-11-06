/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp170;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DailySalesReport {
    private LocalDate saleDate;
    private BigDecimal dailySales;
    private BigDecimal cumulativeSales;
    private Integer transactionCount;
    private BigDecimal avgTransactionAmount;
    private BigDecimal growthPercent;
}
