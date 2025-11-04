/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для информации о non-repeatable reads.
 * Содержит данные о повторном чтении данных, которые изменились между чтениями.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NonRepeatableReadResult {
    private String sessionId;
    private String isolationLevel;
    private Long accountId;
    private BigDecimal firstReadBalance;
    private BigDecimal secondReadBalance;
    private Boolean nonRepeatableReadDetected;
    private LocalDateTime firstReadTime;
    private LocalDateTime secondReadTime;
    private String concurrentTransactionId;
}

