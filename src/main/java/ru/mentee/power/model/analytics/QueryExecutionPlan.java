/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.analytics;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryExecutionPlan {
    private String query;
    private String planText;
    private BigDecimal totalCost;
    private BigDecimal executionTime;
    private BigDecimal planningTime;
    private List<PlanNode> nodes;
    private String performanceAnalysis;
    private List<String> recommendations;
}
