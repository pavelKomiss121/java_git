/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp167;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для результата демонстрации аномалии конкурентности.
 * Содержит информацию о типе аномалии, уровне изоляции и деталях выполнения.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcurrencyAnomalyResult {
    private String anomalyType; // DIRTY_READ, NON_REPEATABLE_READ, PHANTOM_READ, LOST_UPDATE
    private String isolationLevel;
    private Boolean anomalyDetected;
    private String detailedDescription;
    private List<String> executionSteps;
    private String initialValue;
    private String intermediateValue;
    private String finalValue;
    private LocalDateTime executionTime;
    private Long executionDurationMillis;
    private List<String> preventionRecommendations;
}
