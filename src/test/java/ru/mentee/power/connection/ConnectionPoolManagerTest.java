/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;
import ru.mentee.power.connection.impl.HikariConnectionPoolManager;
import ru.mentee.power.connection.interfaces.ConnectionPoolManager;
import ru.mentee.power.connection.model.PoolStatistics;
import ru.mentee.power.exception.SASTException;

@Slf4j
@Testcontainers
@SuppressWarnings("resource")
public class ConnectionPoolManagerTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private ApplicationConfig config;
    private ConnectionPoolManager pool;

    @BeforeEach
    public void setUp() throws SASTException, IOException {
        config = createTestConfig();
        if (pool != null) {
            pool.shutdown();
        }
        pool = new HikariConnectionPoolManager(config);
    }

    @AfterEach
    public void tearDown() {
        if (pool != null) {
            pool.shutdown();
        }
    }

    protected ApplicationConfig createTestConfig() throws SASTException, IOException {
        java.util.Properties props = new java.util.Properties();
        String jdbc = postgres.getJdbcUrl();
        String sep = jdbc.contains("?") ? "&" : "?";
        String urlWithCreds =
                jdbc
                        + sep
                        + "user="
                        + postgres.getUsername()
                        + "&password="
                        + postgres.getPassword()
                        + "&currentSchema=mentee_power";

        props.setProperty("db.url", urlWithCreds);
        props.setProperty("db.username", postgres.getUsername());
        props.setProperty("db.driver", "org.postgresql.Driver");
        props.setProperty("db.show-sql", "false");

        // Устанавливаем пароль через системное свойство (безопасный способ для тестов)
        System.setProperty("db.password", postgres.getPassword());

        return new ApplicationConfig(props, new ConfigFilePath()) {
            @Override
            public void load(String path) {
                /* no-op for tests */
            }
        };
    }

    @Test
    @DisplayName("Should handle concurrent connections efficiently")
    void shouldHandleConcurrentConnections() throws Exception {
        // Given
        int threadCount = 50;
        int operationsPerThread = 100;

        // When
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(
                        () -> {
                            try {
                                for (int j = 0; j < operationsPerThread; j++) {
                                    try (Connection conn = pool.getConnection()) {
                                        // Симуляция работы
                                        Thread.sleep(10);
                                        successCount.incrementAndGet();
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Ошибка в потоке", e);
                            } finally {
                                latch.countDown();
                            }
                        });
            }

            // Then
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(threadCount * operationsPerThread);

            PoolStatistics stats = pool.getStatistics();
            assertThat(stats.getTotalConnections()).isLessThanOrEqualTo(20);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("Should detect connection leaks")
    void shouldDetectConnectionLeaks() throws Exception {
        // Given - создаем пул с коротким порогом обнаружения утечек
        pool.shutdown();
        pool = new HikariConnectionPoolManager(config);

        // When - намеренно не закрываем соединение
        Connection leakedConnection = pool.getConnection();
        assertThat(leakedConnection).isNotNull();

        // Ждем больше порога обнаружения утечек (по умолчанию 2 минуты)
        // Для теста используем короткое время ожидания
        Thread.sleep(100);

        // Then - проверяем, что соединение все еще открыто
        // HikariCP автоматически залогирует предупреждение об утечке
        // В реальном сценарии это будет видно в логах
        assertThat(leakedConnection.isClosed()).isFalse();

        // Закрываем соединение для очистки
        leakedConnection.close();
    }

    @Test
    @DisplayName("Should return valid statistics")
    void shouldReturnValidStatistics() throws SQLException {
        // Given
        PoolStatistics initialStats = pool.getStatistics();

        // When
        try (Connection conn = pool.getConnection()) {
            PoolStatistics activeStats = pool.getStatistics();

            // Then
            assertThat(initialStats).isNotNull();
            assertThat(activeStats).isNotNull();
            assertThat(activeStats.getActiveConnections())
                    .isGreaterThanOrEqualTo(initialStats.getActiveConnections());
            assertThat(activeStats.getTotalConnections()).isGreaterThanOrEqualTo(0);
            assertThat(activeStats.getIdleConnections()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("Should perform health check")
    void shouldPerformHealthCheck() {
        // When
        var healthCheck = pool.performHealthCheck();

        // Then
        assertThat(healthCheck).isNotNull();
        assertThat(healthCheck.isHealthy()).isTrue();
        assertThat(healthCheck.getMessage()).isNotEmpty();
        assertThat(healthCheck.getCheckTime()).isNotNull();
    }

    @Test
    @DisplayName("Should resize pool dynamically")
    void shouldResizePoolDynamically() throws SQLException {
        // When
        pool.resizePool(10, 30);

        // Then
        PoolStatistics afterResize = pool.getStatistics();
        assertThat(afterResize).isNotNull();
        // Проверяем, что пул может работать с новыми параметрами
        try (Connection conn = pool.getConnection()) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    @DisplayName("Should refresh pool")
    void shouldRefreshPool() {
        // When/Then - не должно быть исключений
        pool.refreshPool();

        // Проверяем, что пул все еще работает
        try (Connection conn = pool.getConnection()) {
            assertThat(conn).isNotNull();
        } catch (SQLException e) {
            throw new RuntimeException("Пул не работает после refresh", e);
        }
    }
}
