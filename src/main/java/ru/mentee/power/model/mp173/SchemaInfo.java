/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp173;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Информация о схеме базы данных.
 * Содержит общую информацию о БД, драйвере и версиях.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaInfo {
    private String databaseProductName;
    private String databaseVersion;
    private String driverName;
    private String jdbcVersion;
    private String schemaName;
}
