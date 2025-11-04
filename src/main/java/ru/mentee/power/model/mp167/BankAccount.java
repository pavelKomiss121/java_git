/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp167;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для модели банковского счета.
 * Содержит информацию о счете, его балансе, типе и статусе.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {
    private Long id;
    private Long ownerId;
    private BigDecimal balance;
    private String accountType; // CHECKING, SAVINGS, BUSINESS
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status; // ACTIVE, FROZEN, CLOSED
}
