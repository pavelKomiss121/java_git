/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.entity;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSalesInfo {
    private UUID productId;
    private String productName;
    private Long totalOrdersCount;
    private Long totalQuantitySold;
}
