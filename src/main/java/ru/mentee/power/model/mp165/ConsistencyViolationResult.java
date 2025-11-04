/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp165;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для демонстрации нарушений согласованности данных.
 * Содержит информацию о несоответствии данных между таблицами.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsistencyViolationResult {
    private String violationType;
    private Long entityId;
    private String entityType;
    private BigDecimal expectedValue;
    private BigDecimal actualValue;
    private String description;
    private LocalDateTime detectedAt;
    private String affectedTables;
    private Boolean isResolved;
}
