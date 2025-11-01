/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.analytics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserOrderStats {
    private Long userId;
    private String userName;
    private String email;
    private Integer ordersCount;
    private BigDecimal totalSpent;
    private BigDecimal avgOrderValue;
}
