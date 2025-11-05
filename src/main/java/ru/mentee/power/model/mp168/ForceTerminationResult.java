/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ForceTerminationResult {
    private Boolean successful;
    private Integer totalBlockedProcesses;
    private Integer terminatedProcesses;
    private List<TerminatedProcess> terminatedDetails;
    private String operationReason;
    private LocalDateTime executedAt;
    private String warningMessage;
}
