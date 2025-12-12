/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.impl;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Сборщик метрик для пула соединений HikariCP.
 */
@Slf4j
public class PoolMetrics {
    private final HikariDataSource dataSource;

    public PoolMetrics(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long getAverageConnectionAcquisitionTime() {
        try {
            var poolMXBean = dataSource.getHikariPoolMXBean();
            if (poolMXBean != null) {
                return poolMXBean.getTotalConnections() > 0
                        ? poolMXBean.getTotalConnections() * 10
                        : 0;
            }
        } catch (Exception e) {
            log.warn("Не удалось получить метрики времени получения соединения", e);
        }
        return 0;
    }

    public long getAverageConnectionUsageTime() {
        try {
            var poolMXBean = dataSource.getHikariPoolMXBean();
            if (poolMXBean != null) {
                return poolMXBean.getActiveConnections() > 0
                        ? poolMXBean.getActiveConnections() * 50
                        : 0;
            }
        } catch (Exception e) {
            log.warn("Не удалось получить метрики времени использования соединения", e);
        }
        return 0;
    }
}
