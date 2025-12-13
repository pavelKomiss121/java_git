/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IsolationTestResult {
    private int isolationLevel;
    private String scenario;
    private boolean anomalyDetected;
    private String description;

    public boolean isAnomalyPrevented() {
        return !anomalyDetected;
    }
}
