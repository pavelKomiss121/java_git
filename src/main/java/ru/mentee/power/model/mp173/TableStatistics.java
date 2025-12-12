/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp173;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Статистика использования таблицы.
 * Содержит информацию о размере таблицы, количестве строк, использовании индексов и операциях обслуживания.
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
    private Long insertions;
    private Long updates;
    private Long deletions;
    private LocalDateTime vacuumLastRun;
    private LocalDateTime analyzeLastRun;
    private Long deadTuples;
    private Long liveTuples;
}
