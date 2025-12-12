/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.impl;

import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.connection.interfaces.PoolConfigurator;
import ru.mentee.power.connection.model.PoolConfiguration;
import ru.mentee.power.connection.model.UsageMetrics;

/**
 * Конфигуратор пулов для разных сценариев использования.
 */
@Slf4j
public class DefaultPoolConfigurator implements PoolConfigurator {
    private static final String DEFAULT_POOL_NAME = "mentee-power-pool";
    private static final String DEFAULT_VALIDATION_QUERY = "SELECT 1";

    @Override
    public PoolConfiguration createHighLoadConfiguration() {
        log.info("Создание конфигурации для высоконагруженного API");
        return PoolConfiguration.builder()
                .minimumIdle(10)
                .maximumPoolSize(50)
                .connectionTimeout(10000) // 10 секунд - быстрый таймаут
                .idleTimeout(300000) // 5 минут - короткое время жизни idle
                .maxLifetime(900000) // 15 минут - короткое время жизни соединений
                .validationTimeout(3000) // 3 секунды
                .leakDetectionThreshold(60000) // 1 минута - агрессивное обнаружение утечек
                .validationQuery(DEFAULT_VALIDATION_QUERY)
                .poolName(DEFAULT_POOL_NAME + "-highload")
                .build();
    }

    @Override
    public PoolConfiguration createBatchProcessingConfiguration() {
        log.info("Создание конфигурации для batch обработки");
        return PoolConfiguration.builder()
                .minimumIdle(5)
                .maximumPoolSize(30)
                .connectionTimeout(60000) // 60 секунд - длинный таймаут для долгих операций
                .idleTimeout(1800000) // 30 минут - длинное время жизни idle
                .maxLifetime(3600000) // 60 минут - длинное время жизни соединений
                .validationTimeout(10000) // 10 секунд
                .leakDetectionThreshold(600000) // 10 минут - более мягкое обнаружение утечек
                .validationQuery(DEFAULT_VALIDATION_QUERY)
                .poolName(DEFAULT_POOL_NAME + "-batch")
                .build();
    }

    @Override
    public PoolConfiguration createMicroserviceConfiguration() {
        log.info("Создание конфигурации для микросервисов");
        return PoolConfiguration.builder()
                .minimumIdle(2)
                .maximumPoolSize(10)
                .connectionTimeout(20000) // 20 секунд
                .idleTimeout(300000) // 5 минут - агрессивный eviction
                .maxLifetime(1800000) // 30 минут
                .validationTimeout(5000) // 5 секунд
                .leakDetectionThreshold(120000) // 2 минуты
                .validationQuery(DEFAULT_VALIDATION_QUERY)
                .poolName(DEFAULT_POOL_NAME + "-microservice")
                .build();
    }

    @Override
    public PoolConfiguration autoTuneConfiguration(UsageMetrics metrics) {
        log.info("Автоматическая настройка конфигурации на основе метрик");
        if (metrics == null) {
            log.warn("Метрики не предоставлены, используется конфигурация по умолчанию");
            return createMicroserviceConfiguration();
        }

        // Анализируем метрики
        long avgAcquisitionTime = metrics.getAverageAcquisitionTime();
        long avgUsageTime = metrics.getAverageUsageTime();
        int peakActive = metrics.getPeakActiveConnections();
        long totalRequested = metrics.getTotalConnectionsRequested();

        // Определяем оптимальные параметры
        int minIdle = Math.max(2, peakActive / 4);
        int maxPoolSize = Math.max(10, (int) (peakActive * 1.5));

        // Настраиваем таймауты на основе времени использования
        long connectionTimeout = Math.max(10000, avgAcquisitionTime * 10);
        long idleTimeout = Math.max(300000, avgUsageTime * 20);
        long maxLifetime = Math.max(1800000, avgUsageTime * 100);

        // Если много запросов, увеличиваем пул
        if (totalRequested > 10000) {
            maxPoolSize = Math.max(maxPoolSize, 30);
        }

        log.info(
                "Автоматически настроено: minIdle={}, maxPoolSize={}, connectionTimeout={}ms",
                minIdle,
                maxPoolSize,
                connectionTimeout);

        return PoolConfiguration.builder()
                .minimumIdle(minIdle)
                .maximumPoolSize(maxPoolSize)
                .connectionTimeout(connectionTimeout)
                .idleTimeout(idleTimeout)
                .maxLifetime(maxLifetime)
                .validationTimeout(5000)
                .leakDetectionThreshold(120000)
                .validationQuery(DEFAULT_VALIDATION_QUERY)
                .poolName(DEFAULT_POOL_NAME + "-autotuned")
                .build();
    }

    /**
     * Создать конфигурацию по умолчанию для production.
     *
     * @return конфигурация по умолчанию
     */
    public PoolConfiguration createDefaultConfiguration() {
        return PoolConfiguration.builder()
                .minimumIdle(5)
                .maximumPoolSize(20)
                .connectionTimeout(30000) // 30 секунд
                .idleTimeout(600000) // 10 минут
                .maxLifetime(1800000) // 30 минут
                .validationTimeout(5000) // 5 секунд
                .leakDetectionThreshold(120000) // 2 минуты
                .validationQuery(DEFAULT_VALIDATION_QUERY)
                .poolName(DEFAULT_POOL_NAME)
                .build();
    }
}
