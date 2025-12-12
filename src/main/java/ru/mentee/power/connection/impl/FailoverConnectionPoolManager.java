/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.connection.interfaces.ConnectionPoolManager;
import ru.mentee.power.connection.model.DatabaseNode;
import ru.mentee.power.connection.model.HealthCheckResult;
import ru.mentee.power.connection.model.PoolStatistics;

/**
 * Пул с поддержкой failover на резервные серверы БД.
 */
@Slf4j
public class FailoverConnectionPoolManager implements ConnectionPoolManager {
    private final List<HikariDataSource> dataSources;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final HealthChecker healthChecker;
    private final List<Boolean> nodeHealthStatus;

    public FailoverConnectionPoolManager(List<DatabaseNode> nodes) {
        this.dataSources =
                nodes.stream()
                        .filter(DatabaseNode::isEnabled)
                        .map(this::createDataSource)
                        .collect(Collectors.toList());
        this.nodeHealthStatus = new ArrayList<>(dataSources.size());
        for (int i = 0; i < dataSources.size(); i++) {
            nodeHealthStatus.add(true);
        }
        this.healthChecker = new HealthChecker(dataSources, nodeHealthStatus);
        this.healthChecker.start();
        log.info("Failover пул инициализирован с {} узлами", dataSources.size());
    }

    private HikariDataSource createDataSource(DatabaseNode node) {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(node.getJdbcUrl());
        hikariConfig.setUsername(node.getUsername());
        hikariConfig.setPassword(node.getPassword());

        hikariConfig.setMinimumIdle(5);
        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        hikariConfig.setPoolName("failover-pool-" + node.getNodeName());

        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setValidationTimeout(5000);
        hikariConfig.setLeakDetectionThreshold(120000);

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        return new HikariDataSource(hikariConfig);
    }

    @Override
    public Connection getConnection() throws SQLException {
        int attempts = 0;
        SQLException lastException = null;
        int startIndex = currentIndex.get();

        while (attempts < dataSources.size()) {
            int index = (startIndex + attempts) % dataSources.size();

            // Пропускаем нездоровые узлы
            if (!nodeHealthStatus.get(index)) {
                attempts++;
                continue;
            }

            HikariDataSource ds = dataSources.get(index);
            try {
                var poolMXBean = ds.getHikariPoolMXBean();
                if (poolMXBean != null
                        && poolMXBean.getActiveConnections() < ds.getMaximumPoolSize() * 0.8) {
                    Connection connection = ds.getConnection();
                    currentIndex.set(index);
                    return connection;
                }
            } catch (SQLException e) {
                lastException = e;
                log.warn("Не удалось получить соединение от узла {}: {}", index, e.getMessage());
                nodeHealthStatus.set(index, false);
            }

            attempts++;
        }

        throw new SQLException("Не удалось получить соединение ни от одного узла", lastException);
    }

    @Override
    public void shutdown() {
        log.info("Закрытие failover пула соединений...");
        healthChecker.interrupt();
        try {
            healthChecker.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (HikariDataSource ds : dataSources) {
            if (ds != null && !ds.isClosed()) {
                ds.close();
            }
        }
        log.info("Failover пул соединений закрыт");
    }

    @Override
    public PoolStatistics getStatistics() {
        int totalConnections = 0;
        int activeConnections = 0;
        int idleConnections = 0;
        int threadsAwaiting = 0;

        for (HikariDataSource ds : dataSources) {
            try {
                var poolMXBean = ds.getHikariPoolMXBean();
                if (poolMXBean != null) {
                    totalConnections += poolMXBean.getTotalConnections();
                    activeConnections += poolMXBean.getActiveConnections();
                    idleConnections += poolMXBean.getIdleConnections();
                    threadsAwaiting += poolMXBean.getThreadsAwaitingConnection();
                }
            } catch (Exception e) {
                log.warn("Не удалось получить статистику от узла", e);
            }
        }

        return PoolStatistics.builder()
                .totalConnections(totalConnections)
                .activeConnections(activeConnections)
                .idleConnections(idleConnections)
                .threadsAwaitingConnection(threadsAwaiting)
                .build();
    }

    @Override
    public HealthCheckResult performHealthCheck() {
        int healthyNodes = 0;
        int totalNodes = dataSources.size();
        StringBuilder message = new StringBuilder();

        for (int i = 0; i < dataSources.size(); i++) {
            HikariDataSource ds = dataSources.get(i);
            boolean isHealthy = nodeHealthStatus.get(i);

            if (isHealthy) {
                try (Connection conn = ds.getConnection();
                        Statement stmt = conn.createStatement()) {
                    stmt.execute("SELECT 1");
                    healthyNodes++;
                } catch (SQLException e) {
                    nodeHealthStatus.set(i, false);
                    message.append("Узел ")
                            .append(i)
                            .append(" недоступен: ")
                            .append(e.getMessage())
                            .append(". ");
                }
            } else {
                message.append("Узел ").append(i).append(" помечен как нездоровый. ");
            }
        }

        boolean overallHealthy = healthyNodes > 0;
        if (message.length() == 0) {
            message.append("Все узлы работают нормально");
        }

        PoolStatistics stats = getStatistics();

        return HealthCheckResult.builder()
                .healthy(overallHealthy)
                .message(
                        String.format(
                                "Здоровых узлов: %d/%d. %s",
                                healthyNodes, totalNodes, message.toString()))
                .checkTime(Instant.now())
                .activeConnections(stats.getActiveConnections())
                .idleConnections(stats.getIdleConnections())
                .averageConnectionAcquisitionTime(0)
                .averageConnectionUsageTime(0)
                .build();
    }

    @Override
    public void refreshPool() {
        log.info("Обновление failover пула соединений...");
        // HikariCP автоматически управляет соединениями через maxLifetime и idleTimeout
        // Принудительное обновление не требуется, но можно изменить размер пула
        for (HikariDataSource ds : dataSources) {
            ds.setIdleTimeout(ds.getIdleTimeout());
        }
        log.info("Failover пул соединений обновлен");
    }

    @Override
    public void resizePool(int minSize, int maxSize) {
        log.info("Изменение размера failover пула: min={}, max={}", minSize, maxSize);
        for (HikariDataSource ds : dataSources) {
            ds.setMinimumIdle(minSize);
            ds.setMaximumPoolSize(maxSize);
        }
        log.info("Размер failover пула изменен");
    }

    /**
     * Health checker для автоматического исключения проблемных узлов.
     */
    private static class HealthChecker extends Thread {
        private final List<HikariDataSource> dataSources;
        private final List<Boolean> nodeHealthStatus;

        public HealthChecker(List<HikariDataSource> dataSources, List<Boolean> nodeHealthStatus) {
            this.dataSources = dataSources;
            this.nodeHealthStatus = nodeHealthStatus;
            setDaemon(true);
            setName("FailoverHealthChecker");
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    Thread.sleep(30000); // Проверка каждые 30 секунд
                    for (int i = 0; i < dataSources.size(); i++) {
                        checkHealth(dataSources.get(i), i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void checkHealth(HikariDataSource ds, int index) {
            try (Connection conn = ds.getConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                if (!nodeHealthStatus.get(index)) {
                    log.info("Узел {} восстановлен", index);
                    nodeHealthStatus.set(index, true);
                }
            } catch (SQLException e) {
                if (nodeHealthStatus.get(index)) {
                    log.error("Узел {} недоступен: {}", index, e.getMessage());
                    nodeHealthStatus.set(index, false);
                }
            }
        }
    }
}
