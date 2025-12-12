/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.impl;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Сбор метрик для connection pool.
 */
@Slf4j
public class PoolMetricsCollector implements MetricsTrackerFactory {
    private final MeterRegistry registry;

    public PoolMetricsCollector(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public IMetricsTracker create(String poolName, PoolStats poolStats) {
        return new MicrometerMetricsTracker(poolName, poolStats, registry);
    }

    private static class MicrometerMetricsTracker implements IMetricsTracker {
        private final Timer connectionAcquisitionTimer;
        private final Timer connectionUsageTimer;
        private final Timer connectionCreationTimer;
        private final String poolName;

        public MicrometerMetricsTracker(
                String poolName, PoolStats poolStats, MeterRegistry registry) {
            this.poolName = poolName;

            this.connectionAcquisitionTimer =
                    Timer.builder("hikari.connection.acquisition")
                            .description("Time to acquire connection from pool")
                            .tag("pool", poolName)
                            .register(registry);

            this.connectionUsageTimer =
                    Timer.builder("hikari.connection.usage")
                            .description("Time connection was borrowed from pool")
                            .tag("pool", poolName)
                            .register(registry);

            this.connectionCreationTimer =
                    Timer.builder("hikari.connection.creation")
                            .description("Time to create new connection")
                            .tag("pool", poolName)
                            .register(registry);
        }

        @Override
        public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
            connectionAcquisitionTimer.record(elapsedAcquiredNanos, TimeUnit.NANOSECONDS);
            if (elapsedAcquiredNanos > TimeUnit.SECONDS.toNanos(1)) {
                log.warn(
                        "Медленное получение соединения: {}ms",
                        TimeUnit.NANOSECONDS.toMillis(elapsedAcquiredNanos));
            }
        }

        @Override
        public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
            connectionUsageTimer.record(elapsedBorrowedMillis, TimeUnit.MILLISECONDS);
            if (elapsedBorrowedMillis > TimeUnit.MINUTES.toMillis(2)) {
                log.warn("Соединение использовалось слишком долго: {}ms", elapsedBorrowedMillis);
            }
        }

        @Override
        public void recordConnectionCreatedMillis(long connectionCreatedMillis) {
            connectionCreationTimer.record(connectionCreatedMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public void recordConnectionTimeout() {
            log.warn("Таймаут получения соединения из пула {}", poolName);
        }
    }
}
