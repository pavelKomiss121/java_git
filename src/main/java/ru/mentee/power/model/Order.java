/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {
    private Long id;
    private Long userId;
    private BigDecimal total;
    private String status; // PENDING, CONFIRMED, SHIPPED, DELIVERED
    private LocalDateTime createdAt;
    private String region;

    @Override
    public String toString() {
        return String.format(
                "Order{id=%d, userId=%d, total=%s, status='%s', region='%s', createdAt=%s}",
                id, userId, total, status, region, createdAt);
    }

    public boolean isDelivered() {
        return "DELIVERED".equals(status);
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
