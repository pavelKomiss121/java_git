/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp167;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для результата операции перевода денег.
 * Содержит информацию о статусе операции, балансах счетов и уровне изоляции.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoneyTransferResult {
    private String status; // SUCCESS, FAILED, SERIALIZATION_FAILURE
    private BigDecimal amount;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal fromAccountBalanceBefore;
    private BigDecimal fromAccountBalanceAfter;
    private BigDecimal toAccountBalanceBefore;
    private BigDecimal toAccountBalanceAfter;
    private String isolationLevel;
    private LocalDateTime executionTime;
    private Long executionDurationMillis;
}
