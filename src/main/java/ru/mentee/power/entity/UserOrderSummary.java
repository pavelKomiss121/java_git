/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.entity;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOrderSummary {
    private UUID userId;
    private String userName;
    private String email;
    private Integer ordersCount;
    private BigDecimal totalSpent;
}
