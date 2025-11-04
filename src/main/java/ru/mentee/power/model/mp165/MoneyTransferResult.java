/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp165;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для результата операции перевода денег.
 * Содержит информацию об успешности операции, транзакции и изменениях балансов счетов.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoneyTransferResult {
    private Boolean success;
    private String transactionId;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private BigDecimal fromAccountNewBalance;
    private BigDecimal toAccountNewBalance;
    private String description;
    private LocalDateTime processedAt;
    private String status;
    private String errorMessage;
    private List<String> validationErrors;
}
