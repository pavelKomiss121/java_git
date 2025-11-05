/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TerminatedProcess {
    private Integer pid;
    private String username;
    private String query;
    private LocalDateTime blockedSince;
    private Long blockedDurationMs;
    private String terminationReason;
}
