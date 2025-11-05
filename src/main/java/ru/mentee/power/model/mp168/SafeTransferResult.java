/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SafeTransferResult {
    private Boolean success;
    private String transferId;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private Integer totalAttempts;
    private Integer deadlockRetries;
    private Long totalExecutionTimeMs;
    private BigDecimal fromAccountNewBalance;
    private BigDecimal toAccountNewBalance;
    private String errorMessage;
    private List<String> retryReasons;
    private LocalDateTime completedAt;
}
