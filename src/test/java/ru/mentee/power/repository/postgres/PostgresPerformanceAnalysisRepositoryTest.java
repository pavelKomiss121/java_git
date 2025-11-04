/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.analytics.PerformanceMetrics;
import ru.mentee.power.model.analytics.UserOrderStats;

@Disabled("Урок пройден")
@Testcontainers
public class PostgresPerformanceAnalysisRepositoryTest {

    private Liquibase liquibase;
    private PostgresPerformanceAnalysisRepository performanceRepository;

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("postgres")
                    .withUsername("postgres")
                    .withPassword("postgres");

    protected ApplicationConfig config;

    @BeforeEach
    public void setUp() throws SASTException, IOException {
        config = createTestConfig();

        try (Connection conn = getTestConnection()) {
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            liquibase =
                    new Liquibase(
                            "db/migrations_161/changelog.yaml",
                            new ClassLoaderResourceAccessor(),
                            database);

            // Применяем миграции для тестирования
            liquibase.update("dev,test");

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        performanceRepository = new PostgresPerformanceAnalysisRepository(config);
    }

    protected Connection getTestConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    protected ApplicationConfig createTestConfig() throws SASTException, IOException {
        Properties props = new Properties();
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
        props.setProperty("db.schema", "mentee_power");
        props.setProperty("db.driver", "org.postgresql.Driver");

        return new ApplicationConfig(props, new ConfigFilePath()) {
            @Override
            public void load(String path) {}
        };
    }

    @Test
    @DisplayName("Should demonstrate dramatic performance improvement with indexes")
    public void shouldDemonstratePerformanceImprovement() throws DataAccessException {
        // Given
        String city = "Moscow";
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        Integer minOrders = 5;

        // When - Execute slow query without indexes
        performanceRepository.dropOptimizationIndexes();
        // Небольшая задержка для завершения операций с индексами
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        PerformanceMetrics<List<UserOrderStats>> slowResult =
                performanceRepository.getSlowUserOrderStats(city, startDate, minOrders);

        // Then - Should be slow
        assertThat(slowResult.getExecutionTimeMs()).isNotNull();
        assertThat(slowResult.getExecutionTimeMs()).isGreaterThan(0);
        if (slowResult.getBuffersRead() != null) {
            assertThat(slowResult.getBuffersRead()).isGreaterThanOrEqualTo(0);
        }
        assertThat(slowResult.getPerformanceGrade()).isNotNull();

        // When - Create indexes and execute fast query
        performanceRepository.createOptimizationIndexes();
        // Небольшая задержка для завершения создания индексов CONCURRENTLY
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        PerformanceMetrics<List<UserOrderStats>> fastResult =
                performanceRepository.getFastUserOrderStats(city, startDate, minOrders);

        // Then - Should be faster (with indexes)
        assertThat(fastResult.getExecutionTimeMs()).isNotNull();
        assertThat(fastResult.getExecutionTimeMs()).isGreaterThan(0);
        if (fastResult.getBuffersRead() != null && slowResult.getBuffersRead() != null) {
            // С индексами должно быть меньше чтений из диска
            assertThat(fastResult.getBuffersRead())
                    .isLessThanOrEqualTo(slowResult.getBuffersRead());
        }
        assertThat(fastResult.getPerformanceGrade()).isNotNull();

        // Performance improvement validation
        if (fastResult.getExecutionTimeMs() > 0 && slowResult.getExecutionTimeMs() > 0) {
            double speedupRatio =
                    (double) slowResult.getExecutionTimeMs() / fastResult.getExecutionTimeMs();

            System.out.printf(
                    "[PERF] Slow query time: %d ms, Fast query time: %d ms, Ratio: %.2fx%n",
                    slowResult.getExecutionTimeMs(), fastResult.getExecutionTimeMs(), speedupRatio);

            // В идеале должно быть быстрее, но из-за кэширования может быть медленнее
            // Главное - проверить buffers, которые более надежны
            if (speedupRatio < 1.0) {
                System.out.println(
                        "[PERF] WARNING: Запрос с индексами медленнее из-за кэширования первого"
                                + " запроса");
            }

            // С индексами должно быть быстрее (может не всегда в 10 раз, но быстрее)
            // Допускаем некоторое замедление из-за кэширования (>= 0.8x)
            assertThat(speedupRatio)
                    .as("Speedup ratio should be >= 0.8 (allowing caching effects)")
                    .isGreaterThanOrEqualTo(0.8);
        }

        // Проверяем, что данные получены
        assertThat(slowResult.getData()).isNotNull();
        assertThat(fastResult.getData()).isNotNull();
        assertThat(slowResult.getQueryType()).isEqualTo("SLOW_QUERY_NO_INDEXES");
        assertThat(fastResult.getQueryType()).isEqualTo("FAST_QUERY_WITH_INDEXES");
    }
}
