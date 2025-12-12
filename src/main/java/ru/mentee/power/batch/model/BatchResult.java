/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchResult {
    private int totalRecords;
    private int successfulRecords;
    private int failedRecords;
    private long executionTimeMs;
    private double recordsPerSecond;

    public static BatchResult empty() {
        return BatchResult.builder()
                .totalRecords(0)
                .successfulRecords(0)
                .failedRecords(0)
                .executionTimeMs(0)
                .recordsPerSecond(0.0)
                .build();
    }

    public static BatchResult failed(int totalRecords) {
        return BatchResult.builder()
                .totalRecords(totalRecords)
                .successfulRecords(0)
                .failedRecords(totalRecords)
                .executionTimeMs(0)
                .recordsPerSecond(0.0)
                .build();
    }

    public BatchResult merge(BatchResult other) {
        return BatchResult.builder()
                .totalRecords(this.totalRecords + other.totalRecords)
                .successfulRecords(this.successfulRecords + other.successfulRecords)
                .failedRecords(this.failedRecords + other.failedRecords)
                .executionTimeMs(this.executionTimeMs + other.executionTimeMs)
                .recordsPerSecond(
                        (this.totalRecords + other.totalRecords) > 0
                                ? ((this.successfulRecords + other.successfulRecords) * 1000.0)
                                        / (this.executionTimeMs + other.executionTimeMs)
                                : 0.0)
                .build();
    }
}
