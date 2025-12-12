/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * Результат проверки здоровья пула соединений.
 */
@Value
@Builder
public class HealthCheckResult {
    boolean healthy;
    String message;
    Instant checkTime;
    int activeConnections;
    int idleConnections;
    long averageConnectionAcquisitionTime;
    long averageConnectionUsageTime;
}
