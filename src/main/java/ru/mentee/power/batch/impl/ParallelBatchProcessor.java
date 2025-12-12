/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.batch.interfaces.BatchProcessor;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.BatchResult;
import ru.mentee.power.batch.model.DetailedBatchResult;
import ru.mentee.power.connection.interfaces.ConnectionPoolManager;

/**
 * Многопоточный batch процессор для максимальной производительности.
 */
@Slf4j
public class ParallelBatchProcessor implements BatchProcessor {
    private final ExecutorService executor;
    private final ConnectionPoolManager connectionPool;
    private final int parallelism;

    public ParallelBatchProcessor(ConnectionPoolManager pool, int parallelism) {
        this.connectionPool = pool;
        this.parallelism = parallelism;
        this.executor = new ForkJoinPool(parallelism);
    }

    @Override
    public <T> BatchResult insert(List<T> records) throws SQLException {
        try {
            return processInParallel(records).get();
        } catch (Exception e) {
            throw new SQLException("Ошибка при параллельной обработке", e);
        }
    }

    @Override
    public <T> BatchResult update(List<T> records) throws SQLException {
        return insert(records);
    }

    @Override
    public BatchResult delete(List<Long> ids) throws SQLException {
        BasicBatchProcessor basicProcessor =
                new BasicBatchProcessor(connectionPool.getConnection());
        return basicProcessor.delete(ids);
    }

    @Override
    public <T> BatchResult upsert(List<T> records) throws SQLException {
        return insert(records);
    }

    @Override
    public <T> DetailedBatchResult processWithDetails(List<T> records, BatchOperation operation)
            throws SQLException {
        ResilientBatchProcessor resilientProcessor =
                new ResilientBatchProcessor(connectionPool.getConnection());
        return resilientProcessor.insertWithErrorHandling(records, operation);
    }

    /**
     * Параллельная обработка больших объемов данных.
     */
    public <T> CompletableFuture<BatchResult> processInParallel(List<T> products) {
        int chunkSize = Math.max(1, products.size() / parallelism);
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();

        for (int i = 0; i < parallelism; i++) {
            int start = i * chunkSize;
            int end = (i == parallelism - 1) ? products.size() : (i + 1) * chunkSize;

            if (start >= products.size()) break;

            List<T> chunk = products.subList(start, end);
            CompletableFuture<BatchResult> future =
                    CompletableFuture.supplyAsync(() -> processChunk(chunk), executor);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(
                        v ->
                                futures.stream()
                                        .map(CompletableFuture::join)
                                        .reduce(BatchResult.empty(), BatchResult::merge));
    }

    private <T> BatchResult processChunk(List<T> chunk) {
        try (Connection conn = connectionPool.getConnection()) {
            BasicBatchProcessor processor = new BasicBatchProcessor(conn);
            return processor.insert(chunk);
        } catch (SQLException e) {
            log.error("Ошибка обработки chunk размером {}", chunk.size(), e);
            return BatchResult.failed(chunk.size());
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
