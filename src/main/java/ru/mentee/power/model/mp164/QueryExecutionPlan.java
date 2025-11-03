/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для плана выполнения запроса.
 * Содержит детальную информацию о плане выполнения SQL запроса.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryExecutionPlan {
    private String originalQuery;
    private String planText;
    private BigDecimal totalCost;
    private BigDecimal executionTime;
    private Long rowsProcessed;
    private BigDecimal planningTime;
    private List<PlanNode> planNodes;
    private String recommendations;
    private Boolean usesIndexes;
}
