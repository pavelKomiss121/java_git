/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp165;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для информации о восстановленных товарах при отмене заказа.
 * Содержит информацию о товаре и количестве, возвращенном на склад.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRestoreResult {
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantityRestored;
    private Integer newStockQuantity;
    private String status;
}
