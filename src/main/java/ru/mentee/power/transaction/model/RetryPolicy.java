/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetryPolicy {
    private int maxAttempts;
    private long initialDelayMs;
    private double backoffMultiplier;
    private long maxDelayMs;

    public static RetryPolicy exponentialBackoff(int maxAttempts, long initialDelayMs) {
        return RetryPolicy.builder()
                .maxAttempts(maxAttempts)
                .initialDelayMs(initialDelayMs)
                .backoffMultiplier(2.0)
                .maxDelayMs(5000)
                .build();
    }

    public long calculateDelay(int attempt) {
        long delay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attempt - 1));
        return Math.min(delay, maxDelayMs);
    }
}
