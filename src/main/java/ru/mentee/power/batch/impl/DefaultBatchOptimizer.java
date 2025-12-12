/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.impl;

import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.batch.interfaces.BatchOptimizer;
import ru.mentee.power.batch.model.BatchMetrics;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.Constraints;
import ru.mentee.power.batch.model.ExecutionPlan;
import ru.mentee.power.batch.model.OptimizationReport;

/**
 * Реализация оптимизатора batch операций.
 */
public class DefaultBatchOptimizer implements BatchOptimizer {
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int MIN_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 10000;

    @Override
    public <T> int findOptimalBatchSize(List<T> sampleData, BatchOperation operation) {
        if (sampleData.isEmpty()) {
            return DEFAULT_BATCH_SIZE;
        }

        int dataSize = sampleData.size();
        int optimalSize = DEFAULT_BATCH_SIZE;

        switch (operation) {
            case INSERT:
            case UPSERT:
                optimalSize = Math.min(dataSize, 2000);
                break;
            case UPDATE:
                optimalSize = Math.min(dataSize, 1500);
                break;
            case DELETE:
                optimalSize = Math.min(dataSize, 3000);
                break;
        }

        return Math.max(MIN_BATCH_SIZE, Math.min(optimalSize, MAX_BATCH_SIZE));
    }

    @Override
    public ExecutionPlan createExecutionPlan(int totalRecords, Constraints constraints) {
        int optimalBatchSize = DEFAULT_BATCH_SIZE;

        if (constraints != null) {
            if (constraints.getMaxBatchSize() > 0) {
                optimalBatchSize = Math.min(DEFAULT_BATCH_SIZE, constraints.getMaxBatchSize());
            }
        }

        int numberOfBatches = (int) Math.ceil((double) totalRecords / optimalBatchSize);
        long estimatedExecutionTimeMs = numberOfBatches * 100; // Примерная оценка
        int recommendedParallelism = Math.min(4, numberOfBatches);

        return ExecutionPlan.builder()
                .optimalBatchSize(optimalBatchSize)
                .numberOfBatches(numberOfBatches)
                .estimatedExecutionTimeMs(estimatedExecutionTimeMs)
                .recommendedParallelism(recommendedParallelism)
                .build();
    }

    @Override
    public OptimizationReport analyzePerformance(List<BatchMetrics> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return OptimizationReport.builder()
                    .recommendedBatchSize(DEFAULT_BATCH_SIZE)
                    .recommendations(List.of("Недостаточно данных для анализа"))
                    .expectedImprovementPercent(0.0)
                    .build();
        }

        double avgThroughput =
                metrics.stream().mapToDouble(BatchMetrics::getThroughput).average().orElse(0.0);
        int avgBatchSize =
                (int)
                        metrics.stream()
                                .mapToInt(BatchMetrics::getBatchSize)
                                .average()
                                .orElse(DEFAULT_BATCH_SIZE);

        List<String> recommendations = new ArrayList<>();
        int recommendedBatchSize = avgBatchSize;

        if (avgThroughput < 1000) {
            recommendations.add("Низкая производительность. Рекомендуется увеличить размер batch");
            recommendedBatchSize = Math.min(avgBatchSize * 2, MAX_BATCH_SIZE);
        } else if (avgThroughput > 10000) {
            recommendations.add(
                    "Высокая производительность. Можно попробовать увеличить batch для еще большей"
                            + " эффективности");
            recommendedBatchSize = Math.min(avgBatchSize * 2, MAX_BATCH_SIZE);
        }

        if (metrics.size() > 5) {
            BatchMetrics last = metrics.get(metrics.size() - 1);
            BatchMetrics first = metrics.get(0);
            if (last.getThroughput() > first.getThroughput() * 1.2) {
                recommendations.add(
                        "Производительность улучшается. Продолжайте использовать текущую"
                                + " стратегию");
            }
        }

        double expectedImprovement = avgThroughput > 0 ? 10.0 : 0.0;

        return OptimizationReport.builder()
                .recommendedBatchSize(recommendedBatchSize)
                .recommendations(recommendations)
                .expectedImprovementPercent(expectedImprovement)
                .build();
    }
}
