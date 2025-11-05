/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LockMonitoringInfo {
    private Integer blockedPid;
    private String blockedUser;
    private String blockedQuery;
    private LocalDateTime blockedSince;
    private Long blockedDurationMs;
    private Integer blockingPid;
    private String blockingUser;
    private String blockingQuery;
    private String lockType;
    private String lockMode;
    private String targetResource;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
}
