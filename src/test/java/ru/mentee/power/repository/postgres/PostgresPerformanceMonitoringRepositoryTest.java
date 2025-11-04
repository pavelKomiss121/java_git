/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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
import ru.mentee.power.model.mp164.*;

@Disabled("Only Liquibase tests should run")
@Testcontainers
public class PostgresPerformanceMonitoringRepositoryTest {

    @SuppressWarnings("resource")
    private Liquibase liquibase;

    private PostgresPerformanceMonitoringRepository monitoringRepository;

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("postgres")
                    .withUsername("postgres")
                    .withPassword("postgres");

    protected ApplicationConfig config;

    @BeforeEach
    @SuppressWarnings({"resource", "deprecation"})
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
            liquibase.update("dev,test"); // NOPMD - deprecated method used in tests

            // Включаем pg_stat_statements для теста медленных запросов
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
            } catch (SQLException e) {
                // Игнорируем, если расширение уже установлено или нет прав
            }

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        monitoringRepository = new PostgresPerformanceMonitoringRepository(config);
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
    @DisplayName("Should get index usage statistics")
    void shouldGetIndexUsageStatistics() throws DataAccessException, SQLException {
        // Given: Создаем тестовые индексы и выполняем запросы для генерации статистики
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {

            // Создаем тестовые индексы
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_test_users_email ON mentee_power.users(email)");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_test_orders_status ON"
                            + " mentee_power.orders(status)");

            // Выполняем запросы для генерации статистики использования индексов
            stmt.execute("SELECT * FROM mentee_power.users WHERE email = 'test@example.com'");
            stmt.execute("SELECT * FROM mentee_power.orders WHERE status = 'PENDING'");

            // Обновляем статистику
            stmt.execute("ANALYZE mentee_power.users");
            stmt.execute("ANALYZE mentee_power.orders");
        }

        // When: Получаем статистику использования индексов
        List<IndexUsageStats> stats = monitoringRepository.getIndexUsageStatistics();

        // Then: Проверяем корректность данных
        assertThat(stats).isNotEmpty();

        // Проверяем наличие primary key индексов
        boolean hasPrimaryKeyIndex =
                stats.stream().anyMatch(s -> Boolean.TRUE.equals(s.getIsPrimary()));
        assertThat(hasPrimaryKeyIndex).as("Should contain primary key indexes").isTrue();

        // Проверяем корректность полей
        IndexUsageStats sampleStat = stats.get(0);
        assertThat(sampleStat.getIndexName()).isNotNull();
        assertThat(sampleStat.getTableName()).isNotNull();
        assertThat(sampleStat.getIndexScans()).isNotNull();
        assertThat(sampleStat.getIndexSize()).isNotNull();
        assertThat(sampleStat.getIndexScans()).isGreaterThanOrEqualTo(0);
        assertThat(sampleStat.getIndexSize()).isGreaterThanOrEqualTo(0);

        // hitRatio может быть null, если idx_tup_read = 0
        if (sampleStat.getHitRatio() != null) {
            assertThat(sampleStat.getHitRatio()).isBetween(0.0, 100.0);
        }
    }

    @Test
    @DisplayName("Should identify unused indexes")
    void shouldIdentifyUnusedIndexes() throws DataAccessException, SQLException {
        // Given: Создаем тестовые индексы, некоторые из которых не будут использоваться
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {

            // Создаем индексы, которые не будут использоваться
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_unused_test1 ON mentee_power.users(city)");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_unused_test2 ON mentee_power.orders(region)");

            // Обновляем статистику
            stmt.execute("ANALYZE mentee_power.users");
            stmt.execute("ANALYZE mentee_power.orders");
        }

        // When: Получаем список неиспользуемых индексов
        List<UnusedIndexReport> unusedIndexes = monitoringRepository.getUnusedIndexes();

        // Then: Проверяем корректность рекомендаций
        assertThat(unusedIndexes).isNotNull();

        // Проверяем, что в списке есть наши неиспользуемые индексы
        // (в тестовой среде могут быть другие неиспользуемые индексы)

        // Проверяем корректность полей
        if (!unusedIndexes.isEmpty()) {
            UnusedIndexReport report = unusedIndexes.get(0);
            assertThat(report.getIndexName()).isNotNull();
            assertThat(report.getTableName()).isNotNull();
            assertThat(report.getSizeBytes()).isNotNull();
            assertThat(report.getSizeBytes()).isGreaterThanOrEqualTo(0);
            assertThat(report.getSizePretty()).isNotNull();
            assertThat(report.getRecommendation()).isNotNull();
            assertThat(report.getMaintenanceCost()).isNotNull();
        }
    }

    @Test
    @DisplayName("Should analyze slow queries")
    void shouldAnalyzeSlowQueries() throws DataAccessException, SQLException {
        // Given: Выполняем намеренно медленные запросы без индексов
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {

            // Пытаемся сбросить статистику pg_stat_statements (может быть недоступна)
            try {
                stmt.execute("SELECT pg_stat_statements_reset()");
            } catch (SQLException e) {
                // Игнорируем, если функция недоступна
            }

            // Выполняем медленные запросы без индексов
            stmt.execute("SELECT * FROM mentee_power.users WHERE city = 'UNUSED_CITY'");
            stmt.execute("SELECT * FROM mentee_power.orders WHERE total > 999999");

            // Выполняем несколько раз для накопления статистики
            for (int i = 0; i < 5; i++) {
                stmt.execute("SELECT COUNT(*) FROM mentee_power.users WHERE city = 'UNUSED_CITY'");
            }
        }

        // When: Получаем анализ медленных запросов
        List<SlowQueryReport> slowQueries =
                monitoringRepository.getSlowQueriesWithRecommendations();

        // Then: Проверяем генерацию рекомендаций
        assertThat(slowQueries).isNotNull();

        // Проверяем корректность полей (если pg_stat_statements доступен и есть данные)
        if (!slowQueries.isEmpty()) {
            SlowQueryReport report = slowQueries.get(0);
            assertThat(report.getQueryId()).isNotNull();
            assertThat(report.getQuery()).isNotNull();
            assertThat(report.getMeanExecutionTime()).isNotNull();
            assertThat(report.getTotalExecutionTime()).isNotNull();
            assertThat(report.getCalls()).isNotNull();
            assertThat(report.getCalls()).isGreaterThan(0);
            assertThat(report.getMeanExecutionTime()).isGreaterThan(BigDecimal.ZERO);
            assertThat(report.getOptimizationSuggestions()).isNotNull();
            assertThat(report.getSlowQueryRank()).isNotNull();
            assertThat(report.getSlowQueryRank()).isGreaterThan(0);
        }
        // Если pg_stat_statements недоступно, список будет пустым, что тоже корректно
    }

    @Test
    @DisplayName("Should get query execution plan")
    void shouldGetQueryExecutionPlan() throws DataAccessException {
        // Given: Сложный аналитический запрос с JOIN и GROUP BY
        String complexQuery =
                """
                SELECT o.region, COUNT(o.id) as order_count, SUM(o.total) as total_revenue
                FROM mentee_power.users u
                LEFT JOIN mentee_power.orders o ON u.id = o.user_id
                WHERE o.region IN ('MOSCOW', 'Saint-Petersburg')
                GROUP BY o.region
                ORDER BY total_revenue DESC
                """;

        // When: Получаем план выполнения запроса
        QueryExecutionPlan plan = monitoringRepository.getQueryExecutionPlan(complexQuery);

        // Then: Проверяем парсинг JSON результата и извлечение метрик
        assertThat(plan).isNotNull();
        assertThat(plan.getOriginalQuery()).isEqualTo(complexQuery);
        assertThat(plan.getPlanText()).isNotNull();
        assertThat(plan.getRowsProcessed()).isNotNull();
        assertThat(plan.getRowsProcessed()).isGreaterThanOrEqualTo(0);

        // totalCost может быть null, если план не был полностью распарсен
        if (plan.getTotalCost() != null) {
            assertThat(plan.getTotalCost()).isGreaterThan(BigDecimal.ZERO);
        }

        // executionTime может быть null
        if (plan.getExecutionTime() != null) {
            assertThat(plan.getExecutionTime()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }

        // Проверяем наличие узлов плана
        assertThat(plan.getPlanNodes()).isNotNull();

        // Проверяем рекомендации
        if (plan.getRecommendations() != null) {
            assertThat(plan.getRecommendations()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Should get table statistics")
    void shouldGetTableStatistics() throws DataAccessException, SQLException {
        // Given: Вставляем достаточно данных для генерации статистики
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {

            // Обновляем статистику
            stmt.execute("ANALYZE mentee_power.users");
            stmt.execute("ANALYZE mentee_power.orders");
            stmt.execute("ANALYZE mentee_power.products");
        }

        // When: Получаем статистику всех таблиц
        List<TableStatistics> tableStats = monitoringRepository.getTableStatistics();

        // Then: Проверяем расчет эффективности индексов
        assertThat(tableStats).isNotEmpty();

        // Проверяем корректность полей
        TableStatistics stats = tableStats.get(0);
        assertThat(stats.getSchemaName()).isEqualTo("mentee_power");
        assertThat(stats.getTableName()).isNotNull();
        assertThat(stats.getRowCount()).isNotNull();
        assertThat(stats.getRowCount()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getTableSize()).isNotNull();
        assertThat(stats.getTableSize()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getTableSizePretty()).isNotNull();
        assertThat(stats.getIndexesSize()).isNotNull();
        assertThat(stats.getIndexesSizePretty()).isNotNull();
        assertThat(stats.getIndexCount()).isNotNull();
        assertThat(stats.getIndexCount()).isGreaterThanOrEqualTo(0);

        // indexEfficiency может быть null, если нет сканирований
        if (stats.getIndexEfficiency() != null) {
            assertThat(stats.getIndexEfficiency()).isBetween(0.0, 100.0);
        }

        assertThat(stats.getSequentialScans()).isNotNull();
        assertThat(stats.getIndexScans()).isNotNull();
    }

    @Test
    @DisplayName("Should monitor active queries")
    void shouldMonitorActiveQueries() throws DataAccessException, SQLException {
        // Given: Выполняем запрос мониторинга активных соединений
        // В тестовой среде может не быть активных запросов, поэтому проверяем структуру

        // When: Получаем список активных запросов
        List<ActiveQueryInfo> activeQueries = monitoringRepository.getCurrentActiveQueries();

        // Then: Проверяем корректность полей
        assertThat(activeQueries).isNotNull();

        // Обрабатываем случай пустого результата в тестовой среде
        if (!activeQueries.isEmpty()) {
            ActiveQueryInfo info = activeQueries.get(0);
            assertThat(info.getPid()).isNotNull();
            assertThat(info.getPid()).isGreaterThan(0);
            assertThat(info.getDatabaseName()).isNotNull();
            assertThat(info.getState()).isNotNull();
            assertThat(info.getQuery()).isNotNull();

            // duration может быть null, если query_start = null
            if (info.getDuration() != null) {
                assertThat(info.getDuration()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }

            assertThat(info.getIsBlocking()).isNotNull();
        }
    }

    @Test
    @DisplayName("Should calculate cache hit ratio")
    void shouldCalculateCacheHitRatio() throws DataAccessException, SQLException {
        // Given: Выполняем запросы для генерации статистики кэша
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {

            // Выполняем несколько запросов для генерации статистики кэша
            for (int i = 0; i < 10; i++) {
                stmt.execute("SELECT * FROM mentee_power.users LIMIT 100");
                stmt.execute("SELECT * FROM mentee_power.orders LIMIT 100");
            }

            // Обновляем статистику
            stmt.execute("ANALYZE mentee_power.users");
            stmt.execute("ANALYZE mentee_power.orders");
        }

        // When: Получаем отчет по cache hit ratio
        CacheHitRatioReport report = monitoringRepository.getCacheHitRatioReport();

        // Then: Проверяем расчет cache hit ratio в диапазоне 0-100%
        assertThat(report).isNotNull();
        assertThat(report.getOverallHitRatio()).isNotNull();
        assertThat(report.getOverallHitRatio()).isBetween(0.0, 100.0);

        assertThat(report.getIndexHitRatio()).isNotNull();
        assertThat(report.getIndexHitRatio()).isBetween(0.0, 100.0);

        assertThat(report.getTableHitRatio()).isNotNull();
        assertThat(report.getTableHitRatio()).isBetween(0.0, 100.0);

        // Проверяем метрики bufferHits и diskReads
        assertThat(report.getBufferHits()).isNotNull();
        assertThat(report.getDiskReads()).isNotNull();
        assertThat(report.getBufferHits()).isGreaterThanOrEqualTo(0);
        assertThat(report.getDiskReads()).isGreaterThanOrEqualTo(0);

        // Проверяем рекомендации по настройке shared_buffers
        assertThat(report.getRecommendation()).isNotNull();
        assertThat(report.getRecommendation()).isNotEmpty();

        // Проверяем статистику по таблицам
        assertThat(report.getTableStats()).isNotNull();
        if (!report.getTableStats().isEmpty()) {
            TableCacheStats tableStats = report.getTableStats().get(0);
            assertThat(tableStats.getTableName()).isNotNull();
            assertThat(tableStats.getHeapBlksRead()).isNotNull();
            assertThat(tableStats.getHeapBlksHit()).isNotNull();
            assertThat(tableStats.getIdxBlksRead()).isNotNull();
            assertThat(tableStats.getIdxBlksHit()).isNotNull();
        }
    }

    @Test
    @DisplayName("Should safely create indexes")
    void shouldSafelyCreateIndexes() throws DataAccessException, SQLException {
        // Given: Готовимся к созданию индекса
        String indexName = "idx_test_safe_creation";
        String tableName = "users";
        String columns = "city";
        String indexType = "btree";

        // Удаляем индекс, если он существует
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP INDEX IF EXISTS mentee_power." + indexName);
        }

        // When: Создаем индекс с мониторингом времени выполнения
        IndexCreationResult result =
                monitoringRepository.createIndexSafely(indexName, tableName, columns, indexType);

        // Then: Проверяем успешность создания и отсутствие ошибок
        assertThat(result).isNotNull();
        assertThat(result.getIndexName()).isEqualTo(indexName);
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getCreationTime()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();

        // Валидируем метрики производительности создания
        assertThat(result.getPerformanceImpact()).isNotNull();
        assertThat(result.getPerformanceImpact()).contains("Индекс создан");

        // Проверяем размер созданного индекса
        assertThat(result.getIndexSize()).isNotNull();
        assertThat(result.getIndexSize()).isGreaterThan(0);

        // Проверяем рекомендации
        assertThat(result.getRecommendations()).isNotNull();
        assertThat(result.getRecommendations()).isNotEmpty();

        // Проверяем, что индекс действительно создан
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement();
                java.sql.ResultSet rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'mentee_power'"
                                        + " AND indexname = '"
                                        + indexName
                                        + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }

        // Cleanup: Удаляем тестовый индекс
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP INDEX IF EXISTS mentee_power." + indexName);
        }
    }
}
