/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.model;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Метрики использования пула соединений за период времени.
 */
@Value
@Builder
public class UsageMetrics {
    Instant startTime;
    Instant endTime;
    long totalConnectionsRequested;
    long totalConnectionsAcquired;
    long totalConnectionsReturned;
    long averageAcquisitionTime;
    long averageUsageTime;
    long maxAcquisitionTime;
    long maxUsageTime;
    int peakActiveConnections;
    int peakIdleConnections;
    List<Long> acquisitionTimeSamples;
    List<Long> usageTimeSamples;
}
