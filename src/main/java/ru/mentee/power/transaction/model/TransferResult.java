/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferResult {
    private boolean successful;
    private Long transactionId;
    private BigDecimal amount;
    private String errorMessage;
}
