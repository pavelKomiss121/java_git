/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.interfaces;

import ru.mentee.power.connection.model.PoolConfiguration;
import ru.mentee.power.connection.model.UsageMetrics;

/**
 * Конфигуратор пулов для разных сценариев.
 */
public interface PoolConfigurator {
    /**
     * Создать конфигурацию для высоконагруженного API.
     *
     * @return конфигурация с большим пулом и быстрыми таймаутами
     */
    PoolConfiguration createHighLoadConfiguration();

    /**
     * Создать конфигурацию для batch обработки.
     *
     * @return конфигурация с длинными транзакциями
     */
    PoolConfiguration createBatchProcessingConfiguration();

    /**
     * Создать конфигурацию для микросервисов.
     *
     * @return конфигурация с малым пулом и агрессивным eviction
     */
    PoolConfiguration createMicroserviceConfiguration();

    /**
     * Автоматически подобрать конфигурацию на основе метрик.
     *
     * @param metrics исторические метрики использования
     * @return оптимальная конфигурация
     */
    PoolConfiguration autoTuneConfiguration(UsageMetrics metrics);
}
