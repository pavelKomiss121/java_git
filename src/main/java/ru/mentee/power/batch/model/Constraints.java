/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Constraints {
    private long maxMemoryBytes;
    private long maxExecutionTimeMs;
    private int maxBatchSize;
}
