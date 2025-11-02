/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp163;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для статистики использования индексов.
 * Содержит информацию о том, как часто и эффективно используется индекс.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexUsageStats {
    private String indexName;
    private String tableName;
    private Long totalScans;

    /**
     * Количество кортежей, прочитанных из индекса (idx_tup_read)
     */
    private Long tuplesRead;

    /**
     * Количество кортежей, возвращенных индексом (idx_tup_fetch)
     */
    private Long tuplesReturned;

    /**
     * Селективность индекса (отношение уникальных значений к общему количеству строк)
     * Значение от 0.0 до 1.0, где 1.0 означает уникальный индекс
     */
    private Double selectivity;

    private Long sizeBytes;

    /**
     * SQL определение индекса (DDL команда создания)
     */
    private String definition;

    /**
     * Рекомендации по использованию индекса
     * (например: "Высокая эффективность", "Редко используется", "Рекомендуется удалить")
     */
    private String recommendedUsage;
}
