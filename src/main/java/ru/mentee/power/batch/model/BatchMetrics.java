/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchMetrics {
    private int batchSize;
    private long executionTimeNanos;
    private double throughput;
}
