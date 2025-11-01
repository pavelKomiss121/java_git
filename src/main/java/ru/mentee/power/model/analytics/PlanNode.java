/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.analytics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanNode {
    private String nodeType;
    private BigDecimal cost;
    private BigDecimal actualTime;
    private Long rows;
    private String operation;
    private String relation;
    private String indexName;
    private String filter;
    private Long buffersHit;
    private Long buffersRead;
}
