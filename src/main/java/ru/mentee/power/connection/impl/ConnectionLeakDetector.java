/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Детектор утечек соединений.
 */
@Slf4j
public class ConnectionLeakDetector {
    private final Map<Connection, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final long leakThresholdMillis;

    public ConnectionLeakDetector(long leakThresholdMillis) {
        this.leakThresholdMillis = leakThresholdMillis;
        // Проверка утечек каждую минуту
        scheduler.scheduleAtFixedRate(this::detectLeaks, 1, 1, TimeUnit.MINUTES);
    }

    public ConnectionLeakDetector() {
        this(TimeUnit.MINUTES.toMillis(2)); // По умолчанию 2 минуты
    }

    public Connection trackConnection(Connection connection) {
        ConnectionInfo info =
                new ConnectionInfo(
                        connection,
                        Thread.currentThread().getName(),
                        getStackTrace(),
                        System.currentTimeMillis());

        activeConnections.put(connection, info);

        // Создаем прокси для отслеживания close()
        return createTrackingProxy(connection);
    }

    private Connection createTrackingProxy(Connection connection) {
        return (Connection)
                Proxy.newProxyInstance(
                        connection.getClass().getClassLoader(),
                        new Class[] {Connection.class},
                        (proxy, method, args) -> {
                            if ("close".equals(method.getName())) {
                                activeConnections.remove(connection);
                            }
                            return method.invoke(connection, args);
                        });
    }

    private String getStackTrace() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        new Throwable().printStackTrace(pw);
        return sw.toString();
    }

    private void detectLeaks() {
        long now = System.currentTimeMillis();
        activeConnections.forEach(
                (conn, info) -> {
                    long duration = now - info.getCheckoutTime();
                    if (duration > leakThresholdMillis) {
                        log.error(
                                "Обнаружена утечка соединения! Открыто {} мс\n"
                                        + "Thread: {}\n"
                                        + "Stack trace:\n{}",
                                duration,
                                info.getThreadName(),
                                info.getStackTrace());
                    }
                });
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getActiveConnectionsCount() {
        return activeConnections.size();
    }

    @Data
    private static class ConnectionInfo {
        private final Connection connection;
        private final String threadName;
        private final String stackTrace;
        private final long checkoutTime;
    }
}
