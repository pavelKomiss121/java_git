/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp163;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для хранения результатов измерения производительности запросов.
 * Содержит детальные метрики выполнения запросов к базе данных.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetrics<T> {
    /**
     * Результат выполнения запроса (данные, возвращенные запросом)
     */
    private T data;

    /**
     * Время выполнения запроса в наносекундах
     */
    private Long executionTimeNanos;

    /**
     * Время выполнения запроса в миллисекундах
     */
    private Long executionTimeMs;

    /**
     * JSON план выполнения запроса (результат EXPLAIN ANALYZE)
     */
    private String queryPlan;

    /**
     * Тип сканирования: "Seq Scan", "Index Scan", "Bitmap Heap Scan", и т.д.
     */
    private String scanType;

    /**
     * Количество буферов, прочитанных из кэша (shared hit blocks)
     */
    private Long buffersHit;

    /**
     * Количество буферов, прочитанных с диска (shared read blocks)
     */
    private Long buffersRead;

    /**
     * Количество строк, просканированных при выполнении запроса
     */
    private Long rowsScanned;

    /**
     * Количество строк, возвращенных запросом
     */
    private Long rowsReturned;

    /**
     * Оценка производительности: "EXCELLENT", "GOOD", "POOR", "CRITICAL"
     */
    private String performanceGrade;

    /**
     * Время выполнения запроса
     */
    private LocalDateTime executedAt;

    /**
     * Список использованных индексов (через запятую, если несколько)
     */
    private String indexesUsed;

    /**
     * Оценка стоимости запроса (Total Cost из плана выполнения)
     */
    private BigDecimal costEstimate;

    /**
     * Рекомендации по оптимизации запроса
     */
    private String optimizationRecommendation;
}
