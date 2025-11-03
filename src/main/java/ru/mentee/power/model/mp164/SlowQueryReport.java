/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для анализа медленных запросов.
 * Содержит статистику и рекомендации по оптимизации медленных запросов.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlowQueryReport {
    private String queryId;
    private String query;
    private BigDecimal meanExecutionTime;
    private BigDecimal totalExecutionTime;
    private Long calls;
    private BigDecimal minTime;
    private BigDecimal maxTime;
    private List<String> recommendedIndexes;
    private String optimizationSuggestions;
    private Integer slowQueryRank;
}

