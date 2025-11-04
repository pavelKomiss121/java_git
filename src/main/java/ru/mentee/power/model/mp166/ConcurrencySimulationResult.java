/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для метрик симуляции конкурентной нагрузки.
 * Содержит статистику по операциям, времени отклика и ошибкам конкурентности.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcurrencySimulationResult {
    private Integer totalOperations;
    private Double successRate; // Процент успешных операций (0-100)
    private Double averageResponseTime; // Среднее время отклика в миллисекундах
    private Integer deadlockCount;
    private Integer serializationFailureCount;
}
