/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp173;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Запрос на обновление данных через хранимую процедуру.
 * Содержит идентификатор записи и параметры обновления.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRequest {
    private Long recordId;
    private String tableName;
    private Map<String, Object> updateParams;
    private Map<String, Object> whereConditions;
}
