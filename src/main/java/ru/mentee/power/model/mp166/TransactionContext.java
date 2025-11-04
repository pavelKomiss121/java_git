/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для контекста транзакции.
 * Содержит информацию о транзакции, её параметрах и состоянии.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionContext {
    private String transactionId;
    private String sessionId;
    private String isolationLevel;
    private Boolean isReadOnly;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // ACTIVE, COMMITTED, ROLLED_BACK, FAILED
    private Long durationMillis;
}
