/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeadlockStressTestResult {
    private Integer concurrentTransactions;
    private Integer testDurationSeconds;
    private Integer totalTransactionAttempts;
    private Integer successfulTransactions;
    private Integer deadlockOccurrences;
    private Integer timeoutOccurrences;
    private Double deadlockRate;
    private Double successRate;
    private Double avgTransactionTimeMs;
    private Double maxTransactionTimeMs;
    private String systemStability;
    private LocalDateTime testStarted;
    private LocalDateTime testCompleted;
    private List<String> observedPatterns;
}
