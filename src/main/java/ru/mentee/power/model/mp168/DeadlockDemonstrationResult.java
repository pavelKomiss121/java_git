/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeadlockDemonstrationResult {
    private Boolean deadlockOccurred;
    private String deadlockDescription;
    private List<TransactionStep> transaction1Steps;
    private List<TransactionStep> transaction2Steps;
    private String victimTransaction;
    private String survivorTransaction;
    private Long deadlockDetectionTimeMs;
    private String resolutionMethod;
    private LocalDateTime demonstrationStarted;
    private LocalDateTime demonstrationCompleted;
    private String lessonLearned;
}
