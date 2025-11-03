/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для отчета по кэш hit ratio.
 * Содержит статистику эффективности использования кэша базы данных.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheHitRatioReport {
    private Double overallHitRatio;
    private Double indexHitRatio;
    private Double tableHitRatio;
    private Long bufferHits;
    private Long diskReads;
    private String recommendation;
    private List<TableCacheStats> tableStats;
}

