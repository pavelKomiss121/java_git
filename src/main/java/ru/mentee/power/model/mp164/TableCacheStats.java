/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для статистики кэша таблиц.
 * Содержит детальную информацию об использовании кэша для конкретной таблицы.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableCacheStats {
    private String tableName;
    private Double hitRatio;
    private Long heapBlksRead;
    private Long heapBlksHit;
    private Long idxBlksRead;
    private Long idxBlksHit;
}

