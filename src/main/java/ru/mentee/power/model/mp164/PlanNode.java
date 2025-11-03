/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для узла плана выполнения.
 * Представляет один узел в дереве плана выполнения запроса PostgreSQL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanNode {
    private String nodeType;
    private BigDecimal cost;
    private BigDecimal actualTime;
    private Long rows;
    private String relationName;
    private String indexName;
    private String condition;
}
