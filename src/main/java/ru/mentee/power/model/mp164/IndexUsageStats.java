/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для статистики использования индексов.
 * Содержит детальную информацию об использовании индексов в базе данных.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexUsageStats {
    private String schemaName;
    private String tableName;
    private String indexName;
    private Long indexSize;
    private Long indexScans;
    private Long tuplesRead;
    private Long tuplesReturned;
    private Double hitRatio;
    private String indexType;
    private String indexDefinition;
    private Boolean isUnique;
    private Boolean isPrimary;
}
