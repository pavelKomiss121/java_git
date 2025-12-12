/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp173;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Результат выполнения batch процедуры.
 * Содержит информацию об успешности выполнения, количестве обработанных записей и ошибках.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchProcedureResult {
    private Boolean success;
    private Integer processedCount;
    private Integer successCount;
    private Integer failedCount;
    private List<Integer> batchResults;
    private Long executionTimeMs;
    private Double recordsPerSecond;
    private String errorMessage;
    private List<String> errors;

    public void addBatchResult(int result) {
        if (this.batchResults == null) {
            this.batchResults = new ArrayList<>();
        }
        this.batchResults.add(result);
    }

    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
    }
}
