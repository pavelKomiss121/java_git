/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubqueryPerformanceComparison {
    private String comparisonMethod;
    private Long subqueryExecutionTimeMs;
    private Long joinExecutionTimeMs;
    private Long performanceDifferenceMs;
    private Double performanceRatio;
    private Integer rowsReturned;
    private String winner;
    private String recommendation;
    private List<String> executionDetails;
}
