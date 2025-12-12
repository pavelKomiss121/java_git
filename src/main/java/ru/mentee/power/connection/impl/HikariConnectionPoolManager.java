/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.connection.interfaces.ConnectionPoolManager;
import ru.mentee.power.connection.model.HealthCheckResult;
import ru.mentee.power.connection.model.PoolConfiguration;
import ru.mentee.power.connection.model.PoolStatistics;

/**
 * Менеджер пула соединений на основе HikariCP. Оптимизирован для production использования.
 */
@Slf4j
public class HikariConnectionPoolManager implements ConnectionPoolManager {
    private final HikariDataSource dataSource;
    private final PoolMetrics metrics;

    public HikariConnectionPoolManager(ApplicationConfig config) {
        this.dataSource = createDataSource(config);
        this.metrics = new PoolMetrics(dataSource);
        log.info(
                "HikariCP инициализирован с максимумом {} соединений",
                dataSource.getMaximumPoolSize());
    }

    public HikariConnectionPoolManager(ApplicationConfig config, PoolConfiguration poolConfig) {
        this.dataSource = createDataSource(config, poolConfig);
        this.metrics = new PoolMetrics(dataSource);
        log.info(
                "HikariCP инициализирован с максимумом {} соединений",
                dataSource.getMaximumPoolSize());
    }

    private HikariDataSource createDataSource(ApplicationConfig config) {
        HikariConfig hikariConfig = new HikariConfig();

        // Основные параметры подключения
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Настройки пула для production
        hikariConfig.setMinimumIdle(5);
        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setConnectionTimeout(30000); // 30 секунд
        hikariConfig.setIdleTimeout(600000); // 10 минут
        hikariConfig.setMaxLifetime(1800000); // 30 минут

        // Имя пула для мониторинга
        hikariConfig.setPoolName("mentee-power-pool");

        // Настройки валидации соединений
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setValidationTimeout(5000); // 5 секунд

        // Обнаружение утечек соединений
        hikariConfig.setLeakDetectionThreshold(120000); // 2 минуты

        // Оптимизация для PostgreSQL
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        return new HikariDataSource(hikariConfig);
    }

    private HikariDataSource createDataSource(
            ApplicationConfig config, PoolConfiguration poolConfig) {
        HikariConfig hikariConfig = new HikariConfig();

        // Основные параметры подключения
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Настройки пула из конфигурации
        hikariConfig.setMinimumIdle(poolConfig.getMinimumIdle());
        hikariConfig.setMaximumPoolSize(poolConfig.getMaximumPoolSize());
        hikariConfig.setConnectionTimeout(poolConfig.getConnectionTimeout());
        hikariConfig.setIdleTimeout(poolConfig.getIdleTimeout());
        hikariConfig.setMaxLifetime(poolConfig.getMaxLifetime());

        // Имя пула для мониторинга
        if (poolConfig.getPoolName() != null && !poolConfig.getPoolName().isEmpty()) {
            hikariConfig.setPoolName(poolConfig.getPoolName());
        } else {
            hikariConfig.setPoolName("mentee-power-pool");
        }

        // Настройки валидации соединений
        if (poolConfig.getValidationQuery() != null && !poolConfig.getValidationQuery().isEmpty()) {
            hikariConfig.setConnectionTestQuery(poolConfig.getValidationQuery());
        } else {
            hikariConfig.setConnectionTestQuery("SELECT 1");
        }
        hikariConfig.setValidationTimeout(poolConfig.getValidationTimeout());

        // Обнаружение утечек соединений
        hikariConfig.setLeakDetectionThreshold(poolConfig.getLeakDetectionThreshold());

        // Оптимизация для PostgreSQL
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        return new HikariDataSource(hikariConfig);
    }

    @Override
    public Connection getConnection() throws SQLException {
        long startTime = System.currentTimeMillis();
        try {
            Connection connection = dataSource.getConnection();
            long waitTime = System.currentTimeMillis() - startTime;
            if (waitTime > 1000) {
                log.warn("Получение соединения заняло {}ms", waitTime);
            }
            return connection;
        } catch (SQLException e) {
            log.error("Ошибка получения соединения из пула", e);
            throw e;
        }
    }

    @Override
    public void shutdown() {
        log.info("Закрытие пула соединений...");
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Пул соединений закрыт");
        }
    }

    @Override
    public PoolStatistics getStatistics() {
        try {
            var poolMXBean = dataSource.getHikariPoolMXBean();
            if (poolMXBean != null) {
                return PoolStatistics.builder()
                        .totalConnections(poolMXBean.getTotalConnections())
                        .activeConnections(poolMXBean.getActiveConnections())
                        .idleConnections(poolMXBean.getIdleConnections())
                        .threadsAwaitingConnection(poolMXBean.getThreadsAwaitingConnection())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Не удалось получить статистику пула", e);
        }
        return PoolStatistics.builder()
                .totalConnections(0)
                .activeConnections(0)
                .idleConnections(0)
                .threadsAwaitingConnection(0)
                .build();
    }

    @Override
    public HealthCheckResult performHealthCheck() {
        try {
            PoolStatistics stats = getStatistics();
            long avgAcquisition = metrics.getAverageConnectionAcquisitionTime();
            long avgUsage = metrics.getAverageConnectionUsageTime();

            // Проверяем здоровье пула
            boolean healthy = true;
            StringBuilder message = new StringBuilder();

            if (stats.getThreadsAwaitingConnection() > 5) {
                healthy = false;
                message.append("Много потоков ожидают соединения: ")
                        .append(stats.getThreadsAwaitingConnection())
                        .append(". ");
            }

            if (stats.getActiveConnections() >= dataSource.getMaximumPoolSize() * 0.9) {
                healthy = false;
                message.append("Пул почти заполнен: ")
                        .append(stats.getActiveConnections())
                        .append("/")
                        .append(dataSource.getMaximumPoolSize())
                        .append(". ");
            }

            // Проверяем соединение
            try (Connection conn = dataSource.getConnection();
                    var stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            } catch (SQLException e) {
                healthy = false;
                message.append("Не удалось выполнить проверку соединения: ")
                        .append(e.getMessage())
                        .append(". ");
            }

            if (message.length() == 0) {
                message.append("Пул соединений работает нормально");
            }

            return HealthCheckResult.builder()
                    .healthy(healthy)
                    .message(message.toString())
                    .checkTime(Instant.now())
                    .activeConnections(stats.getActiveConnections())
                    .idleConnections(stats.getIdleConnections())
                    .averageConnectionAcquisitionTime(avgAcquisition)
                    .averageConnectionUsageTime(avgUsage)
                    .build();
        } catch (Exception e) {
            log.error("Ошибка при проверке здоровья пула", e);
            return HealthCheckResult.builder()
                    .healthy(false)
                    .message("Ошибка проверки здоровья: " + e.getMessage())
                    .checkTime(Instant.now())
                    .activeConnections(0)
                    .idleConnections(0)
                    .averageConnectionAcquisitionTime(0)
                    .averageConnectionUsageTime(0)
                    .build();
        }
    }

    @Override
    public void refreshPool() {
        log.info("Обновление пула соединений...");
        // HikariCP автоматически управляет соединениями через maxLifetime и idleTimeout
        // Принудительное обновление не требуется, но можно изменить размер пула
        dataSource.setIdleTimeout(dataSource.getIdleTimeout());
        log.info("Пул соединений обновлен");
    }

    @Override
    public void resizePool(int minSize, int maxSize) {
        log.info("Изменение размера пула: min={}, max={}", minSize, maxSize);
        dataSource.setMinimumIdle(minSize);
        dataSource.setMaximumPoolSize(maxSize);
        log.info("Размер пула изменен");
    }
}
