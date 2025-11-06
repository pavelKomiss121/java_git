/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnsoldProductAnalytics {
    private Long productId;
    private String productName;
    private String sku;
    private String brand;
    private String category;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Integer stockQuantity;
    private LocalDateTime lastSaleDate;
    private Integer daysWithoutSales;
    private BigDecimal inventoryValue;
    private String recommendedAction;
    private String unsoldReason;
}
