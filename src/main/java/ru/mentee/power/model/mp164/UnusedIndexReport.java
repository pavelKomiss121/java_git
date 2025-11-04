/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для отчета о неиспользуемых индексах.
 * Содержит информацию об индексах, которые не используются или используются редко.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnusedIndexReport {
    private String schemaName;
    private String tableName;
    private String indexName;
    private Long sizeBytes;
    private String sizePretty;
    private LocalDateTime lastUsed;
    private Long daysUnused;
    private String recommendation;
    private String maintenanceCost;
}
