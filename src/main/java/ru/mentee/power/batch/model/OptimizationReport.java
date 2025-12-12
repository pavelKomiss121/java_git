/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OptimizationReport {
    private int recommendedBatchSize;
    private List<String> recommendations;
    private double expectedImprovementPercent;
}
