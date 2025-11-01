/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp162;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndexPerformanceTest<T> {
    private T data;
    private Long executionTimeNanos;
    private Long executionTimeMs;
    private String queryPlan;
    private String operationType; // "Seq Scan", "Index Scan", "Bitmap Heap Scan"
    private Long buffersHit;
    private Long buffersRead;
    private Long rowsScanned;
    private Long rowsReturned;
    private String performanceGrade; // "EXCELLENT", "GOOD", "POOR", "CRITICAL"
    private LocalDateTime executedAt;
    private String indexUsed;
    private BigDecimal costEstimate;
}
