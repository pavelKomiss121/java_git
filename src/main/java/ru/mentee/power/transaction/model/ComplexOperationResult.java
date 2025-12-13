/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComplexOperationResult {
    private boolean successful;
    private Long operationId;
    private List<String> completedSteps;
    private List<String> failedSteps;
    private String errorMessage;
}
