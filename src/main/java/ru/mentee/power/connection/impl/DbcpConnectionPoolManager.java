/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.connection.interfaces.ConnectionPoolManager;
import ru.mentee.power.connection.model.HealthCheckResult;
import ru.mentee.power.connection.model.PoolConfiguration;
import ru.mentee.power.connection.model.PoolStatistics;

/**
 * Альтернативный пул на основе Apache DBCP2. Больше возможностей настройки, но медленнее
 * HikariCP.
 */
@Slf4j
public class DbcpConnectionPoolManager implements ConnectionPoolManager {
    private final BasicDataSource dataSource;

    public DbcpConnectionPoolManager(ApplicationConfig config) {
        this.dataSource = createDataSource(config);
        log.info(
                "Apache DBCP2 инициализирован с максимумом {} соединений",
                dataSource.getMaxTotal());
    }

    public DbcpConnectionPoolManager(ApplicationConfig config, PoolConfiguration poolConfig) {
        this.dataSource = createDataSource(config, poolConfig);
        log.info(
                "Apache DBCP2 инициализирован с максимумом {} соединений",
                dataSource.getMaxTotal());
    }

    private BasicDataSource createDataSource(ApplicationConfig config) {
        BasicDataSource ds = new BasicDataSource();

        // Основные параметры
        ds.setDriverClassName(config.getDriver());
        ds.setUrl(config.getUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());

        // Размеры пула
        ds.setInitialSize(5);
        ds.setMinIdle(5);
        ds.setMaxIdle(10);
        ds.setMaxTotal(20);

        // Таймауты
        ds.setMaxWaitMillis(30000);

        // Валидация соединений
        ds.setValidationQuery("SELECT 1");
        ds.setValidationQueryTimeout(5);
        ds.setTestOnBorrow(true);
        ds.setTestOnReturn(false);
        ds.setTestWhileIdle(true);

        // Eviction policy
        ds.setTimeBetweenEvictionRunsMillis(30000);
        ds.setNumTestsPerEvictionRun(3);
        ds.setMinEvictableIdleTimeMillis(600000);

        // Обработка abandoned connections
        ds.setRemoveAbandonedOnBorrow(true);
        ds.setRemoveAbandonedOnMaintenance(true);
        ds.setRemoveAbandonedTimeout(120);
        ds.setLogAbandoned(true);

        // Statement pool
        ds.setPoolPreparedStatements(true);
        ds.setMaxOpenPreparedStatements(100);

        return ds;
    }

    private BasicDataSource createDataSource(
            ApplicationConfig config, PoolConfiguration poolConfig) {
        BasicDataSource ds = new BasicDataSource();

        // Основные параметры
        ds.setDriverClassName(config.getDriver());
        ds.setUrl(config.getUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());

        // Размеры пула из конфигурации
        ds.setInitialSize(poolConfig.getMinimumIdle());
        ds.setMinIdle(poolConfig.getMinimumIdle());
        ds.setMaxIdle(poolConfig.getMaximumPoolSize() / 2);
        ds.setMaxTotal(poolConfig.getMaximumPoolSize());

        // Таймауты
        ds.setMaxWaitMillis(poolConfig.getConnectionTimeout());

        // Валидация соединений
        if (poolConfig.getValidationQuery() != null && !poolConfig.getValidationQuery().isEmpty()) {
            ds.setValidationQuery(poolConfig.getValidationQuery());
        } else {
            ds.setValidationQuery("SELECT 1");
        }
        ds.setValidationQueryTimeout((int) (poolConfig.getValidationTimeout() / 1000));
        ds.setTestOnBorrow(true);
        ds.setTestOnReturn(false);
        ds.setTestWhileIdle(true);

        // Eviction policy
        ds.setTimeBetweenEvictionRunsMillis(30000);
        ds.setNumTestsPerEvictionRun(3);
        ds.setMinEvictableIdleTimeMillis(poolConfig.getIdleTimeout());

        // Обработка abandoned connections
        ds.setRemoveAbandonedOnBorrow(true);
        ds.setRemoveAbandonedOnMaintenance(true);
        ds.setRemoveAbandonedTimeout((int) (poolConfig.getLeakDetectionThreshold() / 1000));
        ds.setLogAbandoned(true);

        // Statement pool
        ds.setPoolPreparedStatements(true);
        ds.setMaxOpenPreparedStatements(100);

        return ds;
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
        log.info("Закрытие пула соединений Apache DBCP2...");
        try {
            if (dataSource != null) {
                dataSource.close();
                log.info("Пул соединений закрыт");
            }
        } catch (SQLException e) {
            log.error("Ошибка при закрытии пула", e);
        }
    }

    @Override
    public PoolStatistics getStatistics() {
        try {
            return PoolStatistics.builder()
                    .totalConnections(dataSource.getNumActive() + dataSource.getNumIdle())
                    .activeConnections(dataSource.getNumActive())
                    .idleConnections(dataSource.getNumIdle())
                    .threadsAwaitingConnection(0) // DBCP2 не предоставляет эту метрику напрямую
                    .build();
        } catch (Exception e) {
            log.warn("Не удалось получить статистику пула", e);
            return PoolStatistics.builder()
                    .totalConnections(0)
                    .activeConnections(0)
                    .idleConnections(0)
                    .threadsAwaitingConnection(0)
                    .build();
        }
    }

    @Override
    public HealthCheckResult performHealthCheck() {
        try {
            PoolStatistics stats = getStatistics();

            // Проверяем здоровье пула
            boolean healthy = true;
            StringBuilder message = new StringBuilder();

            if (stats.getActiveConnections() >= dataSource.getMaxTotal() * 0.9) {
                healthy = false;
                message.append("Пул почти заполнен: ")
                        .append(stats.getActiveConnections())
                        .append("/")
                        .append(dataSource.getMaxTotal())
                        .append(". ");
            }

            // Проверяем соединение
            try (Connection conn = dataSource.getConnection();
                    Statement stmt = conn.createStatement()) {
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
                    .averageConnectionAcquisitionTime(0)
                    .averageConnectionUsageTime(0)
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
        log.info("Обновление пула соединений Apache DBCP2...");
        // DBCP2 автоматически управляет соединениями через eviction policy
        // Принудительное обновление не требуется
        log.info("Пул соединений обновлен");
    }

    @Override
    public void resizePool(int minSize, int maxSize) {
        log.info("Изменение размера пула: min={}, max={}", minSize, maxSize);
        dataSource.setMinIdle(minSize);
        dataSource.setMaxTotal(maxSize);
        log.info("Размер пула изменен");
    }
}
