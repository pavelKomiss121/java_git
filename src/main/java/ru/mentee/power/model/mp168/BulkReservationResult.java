/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BulkReservationResult {
    private Boolean allSuccessful;
    private Integer totalRequests;
    private Integer successfulReservations;
    private Integer failedReservations;
    private List<InventoryReservationResult> results;
    private Integer lockConflicts;
    private Integer timeoutOccurrences;
    private Long totalExecutionTimeMs;
    private String overallStatus;
    private LocalDateTime executedAt;
}
