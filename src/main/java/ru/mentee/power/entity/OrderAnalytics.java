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
public class OrderAnalytics {
    private Long userId;
    private Integer ordersCount;
    private BigDecimal totalSpent;
    private BigDecimal avgOrderValue;
    private String customerType;

    /**
     * Определяет тип клиента на основе общей суммы потраченных средств
     * @param totalSpent общая сумма потраченных средств
     * @return тип клиента: "VIP" (>50000), "REGULAR" (10000-50000), "NEW" (<10000)
     */
    public static String determineCustomerType(BigDecimal totalSpent) {
        if (totalSpent == null) {
            return "NEW";
        }

        int totalSpentInt = totalSpent.intValue();

        if (totalSpentInt > 50000) {
            return "VIP";
        } else if (totalSpentInt >= 10000) {
            return "REGULAR";
        } else {
            return "NEW";
        }
    }

    /**
     * Устанавливает тип клиента автоматически на основе totalSpent
     */
    public void setCustomerTypeAutomatically() {
        this.customerType = determineCustomerType(this.totalSpent);
    }
}
