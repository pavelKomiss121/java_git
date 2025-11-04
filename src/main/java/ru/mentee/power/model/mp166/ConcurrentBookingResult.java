/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для результата конкурентного бронирования товара.
 * Содержит информацию о статусе бронирования, количестве товара и выявленных проблемах.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcurrentBookingResult {
    private String bookingStatus; // SUCCESS, FAILED, RACE_CONDITION
    private Integer requestedQuantity;
    private Integer actualReservedQuantity;
    private Integer stockAfterOperation;
    private List<String> concurrencyIssues;
}
