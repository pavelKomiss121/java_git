/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.model;

import lombok.Builder;
import lombok.Value;

/**
 * Статистика использования пула соединений.
 */
@Value
@Builder
public class PoolStatistics {
    int totalConnections;
    int activeConnections;
    int idleConnections;
    int threadsAwaitingConnection;
}
