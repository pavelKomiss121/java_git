/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryReservationResult {
    private Long productId;
    private String productName;
    private Integer requestedQuantity;
    private Integer actuallyReserved;
    private Boolean successful;
    private String failureReason;
    private Integer remainingStock;
    private String lockStatus;
}
