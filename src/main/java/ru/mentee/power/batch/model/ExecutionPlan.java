/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionPlan {
    private int optimalBatchSize;
    private int numberOfBatches;
    private long estimatedExecutionTimeMs;
    private int recommendedParallelism;
}
