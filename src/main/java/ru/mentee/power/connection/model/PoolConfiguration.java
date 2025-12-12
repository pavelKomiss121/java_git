/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.model;

import lombok.Builder;
import lombok.Value;

/**
 * Конфигурация пула соединений.
 */
@Value
@Builder
public class PoolConfiguration {
    int minimumIdle;
    int maximumPoolSize;
    long connectionTimeout;
    long idleTimeout;
    long maxLifetime;
    long validationTimeout;
    long leakDetectionThreshold;
    String validationQuery;
    String poolName;
}
