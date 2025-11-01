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
public class Product {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Long categoryId;
    private String sku;
    private LocalDateTime createdAt;
    private String categoryName; // Для результата JOIN с categories

    @Override
    public String toString() {
        return String.format(
                "Product{id=%d, name='%s', price=%s, sku='%s', categoryId=%d, categoryName='%s'}",
                id, name, price, sku, categoryId, categoryName);
    }
}
