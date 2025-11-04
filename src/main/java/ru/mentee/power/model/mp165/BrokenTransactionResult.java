/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp165;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для демонстрации сломанных транзакций.
 * Содержит информацию о транзакции, которая была некорректно выполнена.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokenTransactionResult {
    private String transactionId;
    private String operationType;
    private Long accountId;
    private BigDecimal amount;
    private BigDecimal expectedBalance;
    private BigDecimal actualBalance;
    private String issueDescription;
    private LocalDateTime occurredAt;
    private Boolean isRolledBack;
}
