/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.interfaces;

import java.util.List;
import ru.mentee.power.batch.model.BatchMetrics;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.Constraints;
import ru.mentee.power.batch.model.ExecutionPlan;
import ru.mentee.power.batch.model.OptimizationReport;

/**
 * Оптимизатор batch операций.
 */
public interface BatchOptimizer {
    /**
     * Найти оптимальный размер batch для данной операции.
     *
     * @param sampleData образец данных
     * @param operation тип операции
     * @return оптимальный размер batch
     */
    <T> int findOptimalBatchSize(List<T> sampleData, BatchOperation operation);

    /**
     * Создать оптимизированный план выполнения.
     *
     * @param totalRecords общее количество записей
     * @param constraints ограничения (память, время)
     * @return план выполнения batch операций
     */
    ExecutionPlan createExecutionPlan(int totalRecords, Constraints constraints);

    /**
     * Анализировать производительность и предложить улучшения.
     *
     * @param metrics собранные метрики
     * @return рекомендации по оптимизации
     */
    OptimizationReport analyzePerformance(List<BatchMetrics> metrics);
}
