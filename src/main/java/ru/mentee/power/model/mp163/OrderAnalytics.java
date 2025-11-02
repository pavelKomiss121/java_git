/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp163;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для результатов аналитики заказов.
 * Содержит агрегированные данные о заказах по различным критериям.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAnalytics {
    private String region;
    private String status;
    private Integer ordersCount;
    private BigDecimal totalRevenue;
    private BigDecimal avgOrderValue;
    private LocalDateTime firstOrder;
    private LocalDateTime lastOrder;
}
