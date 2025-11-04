/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp165;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для истории транзакций.
 * Содержит информацию о транзакции, её типе, участниках и результате.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistory {
    private String transactionId;
    private String transactionType;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private BigDecimal fromAccountBalanceBefore;
    private BigDecimal fromAccountBalanceAfter;
    private BigDecimal toAccountBalanceBefore;
    private BigDecimal toAccountBalanceAfter;
    private String description;
    private String status;
    private LocalDateTime processedAt;
    private String errorMessage;
}
