/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailedBatchResult {
    private int totalRecords;
    private int successfulRecords;
    private int failedRecords;
    private long executionTimeMs;
    private double recordsPerSecond;
    @Builder.Default private List<FailedRecord> failedRecordsDetails = new ArrayList<>();

    public static class DetailedBatchResultBuilder {
        // Убеждаемся, что Lombok использует наше поле, а не создает новое
        private List<FailedRecord> failedRecordsDetails = new ArrayList<>();

        public DetailedBatchResultBuilder addFailedRecord(FailedRecord record) {
            if (this.failedRecordsDetails == null) {
                this.failedRecordsDetails = new ArrayList<>();
            }
            this.failedRecordsDetails.add(record);
            this.failedRecords++;
            return this;
        }

        // Переопределяем сеттер, чтобы гарантировать, что список не null
        public DetailedBatchResultBuilder failedRecordsDetails(
                List<FailedRecord> failedRecordsDetails) {
            this.failedRecordsDetails =
                    failedRecordsDetails != null
                            ? new ArrayList<>(failedRecordsDetails)
                            : new ArrayList<>();
            return this;
        }

        public DetailedBatchResultBuilder incrementSuccessful() {
            this.successfulRecords++;
            return this;
        }

        public DetailedBatchResultBuilder incrementFailed() {
            this.failedRecords++;
            return this;
        }

        // Переопределяем build(), чтобы гарантировать использование нашего списка
        public DetailedBatchResult build() {
            DetailedBatchResult result = new DetailedBatchResult();
            result.totalRecords = this.totalRecords;
            result.successfulRecords = this.successfulRecords;
            result.failedRecords = this.failedRecords;
            result.executionTimeMs = this.executionTimeMs;
            result.recordsPerSecond = this.recordsPerSecond;
            result.failedRecordsDetails =
                    this.failedRecordsDetails != null
                            ? new ArrayList<>(this.failedRecordsDetails)
                            : new ArrayList<>();
            return result;
        }
    }
}
