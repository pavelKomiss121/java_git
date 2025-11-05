/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryReservationRequest {
    private Long productId;
    private Integer quantity;
    private String reservationReason;
    private Long userId;
}
