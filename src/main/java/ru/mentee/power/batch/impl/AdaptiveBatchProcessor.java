/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.batch.interfaces.BatchProcessor;
import ru.mentee.power.batch.model.BatchMetrics;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.BatchResult;
import ru.mentee.power.batch.model.DetailedBatchResult;

/**
 * Адаптивный batch процессор с автоматической оптимизацией размера batch.
 */
@Slf4j
public class AdaptiveBatchProcessor implements BatchProcessor {
    private int currentBatchSize = 100;
    private final int minBatchSize = 10;
    private final int maxBatchSize = 10000;
    private final Connection connection;
    private final List<BatchMetrics> metrics = new ArrayList<>();

    public AdaptiveBatchProcessor(Connection connection) {
        this.connection = connection;
    }

    @Override
    public <T> BatchResult insert(List<T> records) throws SQLException {
        return processWithAdaptiveBatch(records);
    }

    @Override
    public <T> BatchResult update(List<T> records) throws SQLException {
        BasicBatchProcessor basicProcessor = new BasicBatchProcessor(connection);
        return basicProcessor.update(records);
    }

    @Override
    public BatchResult delete(List<Long> ids) throws SQLException {
        BasicBatchProcessor basicProcessor = new BasicBatchProcessor(connection);
        return basicProcessor.delete(ids);
    }

    @Override
    public <T> BatchResult upsert(List<T> records) throws SQLException {
        return processWithAdaptiveBatch(records);
    }

    @Override
    public <T> DetailedBatchResult processWithDetails(List<T> records, BatchOperation operation)
            throws SQLException {
        ResilientBatchProcessor resilientProcessor = new ResilientBatchProcessor(connection);
        return resilientProcessor.insertWithErrorHandling(records, operation);
    }

    /**
     * Автоматически подбирает оптимальный размер batch.
     */
    public <T> BatchResult processWithAdaptiveBatch(List<T> records) throws SQLException {
        metrics.clear();
        int processed = 0;
        int totalSuccessful = 0;
        int totalFailed = 0;
        long totalTime = 0;

        BasicBatchProcessor processor = new BasicBatchProcessor(connection);

        while (processed < records.size()) {
            int batchEnd = Math.min(processed + currentBatchSize, records.size());
            List<T> batch = records.subList(processed, batchEnd);

            long startTime = System.nanoTime();
            BatchResult result = processor.insert(batch);
            long duration = System.nanoTime() - startTime;

            totalSuccessful += result.getSuccessfulRecords();
            totalFailed += result.getFailedRecords();
            totalTime += TimeUnit.NANOSECONDS.toMillis(duration);

            BatchMetrics metric =
                    BatchMetrics.builder()
                            .batchSize(batch.size())
                            .executionTimeNanos(duration)
                            .throughput(calculateNanoThroughput(batch.size(), duration))
                            .build();

            metrics.add(metric);

            adaptBatchSize(metric);

            processed = batchEnd;

            log.debug(
                    "Обработан batch размером {} за {} мс, throughput: {} rec/s",
                    batch.size(),
                    TimeUnit.NANOSECONDS.toMillis(duration),
                    metric.getThroughput());
        }

        return BatchResult.builder()
                .totalRecords(records.size())
                .successfulRecords(totalSuccessful)
                .failedRecords(totalFailed)
                .executionTimeMs(totalTime)
                .recordsPerSecond(calculateThroughput(totalSuccessful, totalTime))
                .build();
    }

    private void adaptBatchSize(BatchMetrics lastMetric) {
        if (metrics.size() >= 2) {
            BatchMetrics previous = metrics.get(metrics.size() - 2);

            if (lastMetric.getThroughput() > previous.getThroughput() * 1.1) {
                currentBatchSize = Math.min(currentBatchSize * 2, maxBatchSize);
            } else if (lastMetric.getThroughput() < previous.getThroughput() * 0.9) {
                currentBatchSize = Math.max(currentBatchSize / 2, minBatchSize);
            }
        }
    }

    private double calculateNanoThroughput(int records, long timeNanos) {
        if (timeNanos == 0) return 0;
        return (records * 1_000_000_000.0) / timeNanos;
    }

    private double calculateThroughput(int records, long timeMs) {
        if (timeMs == 0) return 0;
        return (records * 1000.0) / timeMs;
    }

    public List<BatchMetrics> getMetrics() {
        return new ArrayList<>(metrics);
    }

    public int getCurrentBatchSize() {
        return currentBatchSize;
    }
}
