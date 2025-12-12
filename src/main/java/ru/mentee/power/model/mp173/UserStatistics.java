/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp173;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Статистика пользователя.
 * Содержит информацию о количестве заказов, общей сумме потраченных средств и среднем чеке.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatistics {
    private Long userId;
    private Integer totalOrders;
    private BigDecimal totalSpent;
    private BigDecimal avgOrderValue;
}
