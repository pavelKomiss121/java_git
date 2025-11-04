/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для операции в рамках транзакции.
 * Содержит информацию об отдельной операции (SELECT, UPDATE, INSERT, DELETE).
 * 
 * @param <T> тип результата операции
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionOperation<T> {
    private String operationId;
    private String transactionId;
    private String operationType; // SELECT, UPDATE, INSERT, DELETE
    private String sqlQuery;
    private LocalDateTime executionTime;
    private Long durationMillis;
    private Integer rowsAffected;
    private Boolean success;
    private String errorMessage;
    private T result;
}

