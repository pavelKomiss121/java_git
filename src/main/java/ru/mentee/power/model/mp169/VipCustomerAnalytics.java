/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VipCustomerAnalytics {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String customerLevel;
    private BigDecimal totalSpent;
    private BigDecimal averageOrderValue;
    private BigDecimal systemAverageOrderValue;
    private BigDecimal averageMultiplier;
    private Integer orderCount;
    private LocalDateTime registrationDate;
    private LocalDateTime lastPurchaseDate;
    private String vipStatusReason;
}
