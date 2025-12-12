/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp173;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Информация об оптимизации таблицы.
 * Содержит информацию о таблицах с отсутствующими индексами на внешних ключах.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableOptimizationInfo {
    private String schemaName;
    private String tableName;
    private Integer foreignKeyCount;
    private Integer indexedForeignKeyCount;
    private String recommendation;
}
