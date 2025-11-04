/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для результата создания индекса.
 * Содержит информацию о результатах создания индекса и его влиянии на производительность.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexCreationResult {
    private String indexName;
    private Boolean success;
    private LocalDateTime creationTime;
    private Long indexSize;
    private String errorMessage;
    private String performanceImpact;
    private String recommendations;
}
