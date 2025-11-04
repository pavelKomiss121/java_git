/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для результата операции.
 * Содержит общую информацию о результате выполнения операции.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResult {
    private String operationId;
    private Boolean success;
    private String status; // SUCCESS, FAILED, PARTIAL, RETRY
    private LocalDateTime executionTime;
    private Long durationMillis;
    private String errorMessage;
    private Integer retryCount;
}
