/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для информации о обнаруженных dirty reads.
 * Содержит данные о сессии, уровне изоляции, балансах и временных метках операций.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirtyReadResult {
    private String sessionId;
    private String isolationLevel;
    private BigDecimal initialBalance;
    private BigDecimal intermediateBalance;
    private BigDecimal finalBalance;
    private Boolean dirtyReadDetected;
    private LocalDateTime operationStartTime;
    private LocalDateTime intermediateReadTime;
    private LocalDateTime operationEndTime;
}

