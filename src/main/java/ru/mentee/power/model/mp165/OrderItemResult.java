/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp165;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для результата добавления товара в заказ.
 * Содержит информацию о товаре, количестве, ценах и статусе резервирования.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResult {
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantityOrdered;
    private Integer quantityReserved;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private Integer newStockQuantity;
    private String status;
}
