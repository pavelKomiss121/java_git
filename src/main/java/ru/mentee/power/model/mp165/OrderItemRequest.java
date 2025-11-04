/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp165;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для запроса на добавление товара в заказ.
 * Содержит информацию о товаре, количестве и примечаниях.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {
    private Long productId;
    private Integer quantity;
    private String notes;
}
