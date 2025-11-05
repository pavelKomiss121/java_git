/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionStep {
    private Integer stepNumber;
    private String operation;
    private String targetResource;
    private String lockType;
    private Boolean successful;
    private String errorMessage;
    private LocalDateTime executedAt;
}
