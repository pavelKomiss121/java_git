/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp167;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для модели банковской транзакции.
 * Содержит информацию о транзакции, её типе, статусе и связанных счетах.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransaction {
    private Long id;
    private Long accountId;
    private Long relatedAccountId; // Для операций типа TRANSFER
    private BigDecimal amount;
    private String transactionType; // DEPOSIT, WITHDRAWAL, TRANSFER
    private String status; // PENDING, COMPLETED, FAILED, CANCELLED
    private String description;
    private String purpose;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
