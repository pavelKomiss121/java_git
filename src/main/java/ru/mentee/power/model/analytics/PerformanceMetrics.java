/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.analytics;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerformanceMetrics<T> {
    private T data;
    private Long executionTimeMs;
    private Long planningTimeMs;
    private Long buffersHit;
    private Long buffersRead;
    private String queryType;
    private LocalDateTime executedAt;
    private String performanceGrade; // EXCELLENT, GOOD, POOR, CRITICAL
}
