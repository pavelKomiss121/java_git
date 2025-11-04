/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для статистики таблиц.
 * Содержит детальную статистику о таблице, включая размер, количество строк, использование индексов.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableStatistics {
    private String schemaName;
    private String tableName;
    private Long rowCount;
    private Long tableSize;
    private String tableSizePretty;
    private Long indexesSize;
    private String indexesSizePretty;
    private Integer indexCount;
    private Double indexEfficiency;
    private Long sequentialScans;
    private Long indexScans;
    private LocalDateTime vacuumLastRun;
    private LocalDateTime analyzeLastRun;
}
