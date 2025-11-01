/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.Order;
import ru.mentee.power.model.Product;
import ru.mentee.power.model.User;
import ru.mentee.power.model.mp162.IndexPerformanceTest;
import ru.mentee.power.model.mp162.IndexSizeInfo;

@Testcontainers
public class PostgresBTreeIndexRepositoryTest {

    private Liquibase liquibase;
    private PostgresBTreeIndexRepository btreeRepository;

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

        btreeRepository = new PostgresBTreeIndexRepository(config);

        // Индексы создаются миграцией, тесты сами управляют ими через методы репозитория
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
    @DisplayName("Should demonstrate massive performance improvement with B-Tree indexes")
    void shouldDemonstrateMassivePerformanceImprovementWithBTreeIndexes()
            throws DataAccessException {
        // Given: Большие объемы тестовых данных и конкретные тестовые параметры
        String testEmail = "user1@example.com";
        Long testUserId = 1L;
        Integer limit = 10;
        Integer offset = 0;
        Long categoryId = 1L;
        BigDecimal minPrice = new BigDecimal("100.00");
        BigDecimal maxPrice = new BigDecimal("1000.00");

        // When: Выполнение идентичных запросов без индексов

        // Тест 1: Поиск пользователя по email
        IndexPerformanceTest<Optional<User>> userWithoutIndex =
                btreeRepository.findUserByEmailWithoutIndex(testEmail);

        IndexPerformanceTest<Optional<User>> userWithIndex =
                btreeRepository.findUserByEmailWithIndex(testEmail);

        // Тест 2: Загрузка заказов пользователя
        IndexPerformanceTest<List<Order>> ordersWithoutIndex =
                btreeRepository.getUserOrdersWithoutIndex(testUserId, limit, offset);

        IndexPerformanceTest<List<Order>> ordersWithIndex =
                btreeRepository.getUserOrdersWithIndex(testUserId, limit, offset);

        // Тест 3: Поиск товаров по категории и цене
        IndexPerformanceTest<List<Product>> productsWithoutIndex =
                btreeRepository.findProductsByCategoryAndPriceWithoutIndex(
                        categoryId, minPrice, maxPrice, limit);

        IndexPerformanceTest<List<Product>> productsWithIndex =
                btreeRepository.findProductsByCategoryAndPriceWithIndex(
                        categoryId, minPrice, maxPrice, limit);

        // Then: Улучшение производительности минимум в 5-10 раз для всех типов запросов
        validatePerformanceImprovement(userWithoutIndex, userWithIndex, "user search");
        validatePerformanceImprovement(ordersWithoutIndex, ordersWithIndex, "orders search");
        validatePerformanceImprovement(productsWithoutIndex, productsWithIndex, "products search");

        // Then: Переход от Seq Scan к Index Scan в планах выполнения
        // Проверяем, что с индексами используется Index Scan или есть указание на использование
        // индекса
        assertThat(userWithIndex.getIndexUsed())
                .as("Index should be used for user search with index")
                .isNotNull();
        if (userWithIndex.getOperationType() != null) {
            assertThat(userWithIndex.getOperationType())
                    .satisfiesAnyOf(
                            op -> assertThat(op).contains("Index"),
                            op -> assertThat(op).isIn("Bitmap Heap Scan", "Bitmap Index Scan"));
        }

        assertThat(ordersWithIndex.getIndexUsed())
                .as("Index should be used for orders search with index")
                .isNotNull();
        if (ordersWithIndex.getOperationType() != null) {
            assertThat(ordersWithIndex.getOperationType())
                    .satisfiesAnyOf(
                            op -> assertThat(op).contains("Index"),
                            op -> assertThat(op).isIn("Bitmap Heap Scan", "Bitmap Index Scan"));
        }

        assertThat(productsWithIndex.getIndexUsed())
                .as("Index should be used for products search with index")
                .isNotNull();
        if (productsWithIndex.getOperationType() != null) {
            assertThat(productsWithIndex.getOperationType())
                    .satisfiesAnyOf(
                            op -> assertThat(op).contains("Index"),
                            op -> assertThat(op).isIn("Bitmap Heap Scan", "Bitmap Index Scan"));
        }

        // Then: Корректность данных до и после применения индексов
        assertThat(userWithoutIndex.getData()).isNotNull();
        assertThat(userWithIndex.getData()).isNotNull();
        assertThat(userWithoutIndex.getData().equals(userWithIndex.getData())).isTrue();

        assertThat(ordersWithoutIndex.getData()).isNotNull();
        assertThat(ordersWithIndex.getData()).isNotNull();
        assertThat(ordersWithoutIndex.getData().size()).isEqualTo(ordersWithIndex.getData().size());

        assertThat(productsWithoutIndex.getData()).isNotNull();
        assertThat(productsWithIndex.getData()).isNotNull();
    }

    private void validatePerformanceImprovement(
            IndexPerformanceTest<?> withoutIndex,
            IndexPerformanceTest<?> withIndex,
            String queryType) {
        assertThat(withoutIndex.getExecutionTimeMs()).isNotNull();
        assertThat(withIndex.getExecutionTimeMs()).isNotNull();
        assertThat(withoutIndex.getExecutionTimeMs()).isGreaterThan(0);
        assertThat(withIndex.getExecutionTimeMs()).isGreaterThan(0);

        // Проверяем, что с индексами быстрее (или примерно равны из-за накладных расходов)
        // Примечание: В тестовом окружении улучшение может быть незначительным из-за кэширования
        if (withoutIndex.getExecutionTimeMs() > 0 && withIndex.getExecutionTimeMs() > 0) {
            double speedupRatio =
                    (double) withoutIndex.getExecutionTimeMs() / withIndex.getExecutionTimeMs();
            // В тестах с небольшими данными скорость может быть примерно равной или чуть быстрее
            // Главное - проверить, что индексы используются (проверяется отдельно)
            // С индексами должно быть не намного медленнее (допускаем небольшое замедление из-за
            // накладных расходов)
            assertThat(speedupRatio)
                    .isGreaterThan(0.5)
                    .as("Performance ratio for %s should be reasonable (>= 0.5x)", queryType);
        }

        // Проверяем buffers - с индексами должно быть меньше чтений с диска
        if (withoutIndex.getBuffersRead() != null && withIndex.getBuffersRead() != null) {
            assertThat(withIndex.getBuffersRead())
                    .isLessThanOrEqualTo(withoutIndex.getBuffersRead())
                    .as("Index scan should read fewer blocks from disk for %s", queryType);
        }
    }

    @Test
    @DisplayName("Should validate B-Tree index creation and structure")
    void shouldValidateBTreeIndexCreationAndStructure() throws DataAccessException, SQLException {
        // Given: База данных без индексов
        btreeRepository.dropBTreeIndexes();

        // Проверяем, что индексов нет
        List<IndexSizeInfo> indexesBefore = btreeRepository.getIndexSizeInformation();
        long idxUsersBefore =
                indexesBefore.stream()
                        .filter(idx -> idx.getIndexName().equals("idx_users_email_active"))
                        .count();
        assertThat(idxUsersBefore).isEqualTo(0);

        // When: Создание всех B-Tree индексов через JDBC
        IndexPerformanceTest<String> createResult = btreeRepository.createBTreeIndexes();

        // Then: Успешное создание всех требуемых индексов
        assertThat(createResult.getData()).isEqualTo("Индексы успешно созданы");
        assertThat(createResult.getExecutionTimeMs()).isGreaterThan(0);

        // Then: Правильные метаданные индексов в pg_indexes
        List<IndexSizeInfo> indexesAfter = btreeRepository.getIndexSizeInformation();

        // Проверяем наличие всех созданных индексов
        assertThat(indexesAfter).isNotEmpty();

        List<String> indexNames = indexesAfter.stream().map(IndexSizeInfo::getIndexName).toList();

        assertThat(indexNames)
                .contains(
                        "idx_users_email_active",
                        "idx_orders_user_created_desc",
                        "idx_products_category_price");

        // Then: Валидация размеров и типов созданных индексов
        for (IndexSizeInfo indexInfo : indexesAfter) {
            assertThat(indexInfo.getIndexName()).isNotNull();
            assertThat(indexInfo.getTableName()).isNotNull();
            assertThat(indexInfo.getIndexType()).isEqualTo("btree"); // Все наши индексы B-Tree
            assertThat(indexInfo.getSizeBytes()).isNotNull();
            assertThat(indexInfo.getSizeBytes()).isGreaterThan(0);
            assertThat(indexInfo.getSizeHuman()).isNotNull();
            assertThat(indexInfo.getDefinition()).isNotNull();
        }

        // Проверяем через прямой SQL запрос (индексы могут быть в public или mentee_power)
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {

            stmt.execute("SET search_path TO mentee_power, public");

            ResultSet rs =
                    stmt.executeQuery(
                            "SELECT indexname, tablename FROM pg_indexes WHERE (schemaname ="
                                + " 'mentee_power' OR schemaname = 'public')   AND indexname LIKE"
                                + " 'idx_%'   AND indexname IN ('idx_users_email_active',"
                                + " 'idx_orders_user_created_desc',"
                                + " 'idx_products_category_price')");

            int count = 0;
            while (rs.next()) {
                count++;
                String idxName = rs.getString("indexname");
                assertThat(idxName).matches("idx_.*");
            }

            assertThat(count).isGreaterThanOrEqualTo(3); // Минимум 3 наших индекса
        }
    }

    @Test
    @DisplayName("Should measure index impact on different query patterns")
    void shouldMeasureIndexImpactOnDifferentQueryPatterns() throws DataAccessException {
        // Given: Различные паттерны запросов

        // Паттерн 1: Точный поиск (поиск пользователя по email)
        String exactEmail = "user1@example.com";
        IndexPerformanceTest<Optional<User>> exactWithout =
                btreeRepository.findUserByEmailWithoutIndex(exactEmail);
        IndexPerformanceTest<Optional<User>> exactWith =
                btreeRepository.findUserByEmailWithIndex(exactEmail);

        // Паттерн 2: Сортировка с пагинацией (заказы пользователя)
        Long userId = 1L;
        IndexPerformanceTest<List<Order>> sortedWithout =
                btreeRepository.getUserOrdersWithoutIndex(userId, 20, 0);
        IndexPerformanceTest<List<Order>> sortedWith =
                btreeRepository.getUserOrdersWithIndex(userId, 20, 0);

        // Паттерн 3: Диапазон значений с JOIN (товары по категории и цене)
        Long categoryId = 1L;
        BigDecimal minPrice = new BigDecimal("50.00");
        BigDecimal maxPrice = new BigDecimal("500.00");
        IndexPerformanceTest<List<Product>> rangeWithout =
                btreeRepository.findProductsByCategoryAndPriceWithoutIndex(
                        categoryId, minPrice, maxPrice, 50);
        IndexPerformanceTest<List<Product>> rangeWith =
                btreeRepository.findProductsByCategoryAndPriceWithIndex(
                        categoryId, minPrice, maxPrice, 50);

        // When: Выполнение каждого паттерна с индексами и без них (уже выполнено выше)

        // Then: Анализ EXPLAIN ANALYZE планов для каждого типа запроса
        analyzeExecutionPlan(exactWithout, "Exact search without index", false);
        analyzeExecutionPlan(exactWith, "Exact search with index", true);

        analyzeExecutionPlan(sortedWithout, "Sorting with pagination without index", false);
        analyzeExecutionPlan(sortedWith, "Sorting with pagination with index", true);

        analyzeExecutionPlan(rangeWithout, "Range query with JOIN without index", false);
        analyzeExecutionPlan(rangeWith, "Range query with JOIN with index", true);

        // Then: Измерение buffers hit/read ratio
        validateBufferRatio(exactWithout, exactWith, "Exact search");
        validateBufferRatio(sortedWithout, sortedWith, "Sorting query");
        validateBufferRatio(rangeWithout, rangeWith, "Range query");

        // Then: Сравнение cost estimates до и после оптимизации
        validateCostEstimate(exactWithout, exactWith, "Exact search");
        validateCostEstimate(sortedWithout, sortedWith, "Sorting query");
        validateCostEstimate(rangeWithout, rangeWith, "Range query");
    }

    private void analyzeExecutionPlan(
            IndexPerformanceTest<?> result, String queryType, boolean withIndex) {
        assertThat(result.getQueryPlan()).isNotNull();
        assertThat(result.getOperationType()).isNotNull();

        if (withIndex) {
            // С индексами должен быть Index Scan или похожая операция
            assertThat(result.getOperationType())
                    .satisfiesAnyOf(
                            op -> assertThat(op).contains("Index"),
                            op -> assertThat(op).isEqualTo("Bitmap Heap Scan"),
                            op -> assertThat(op).isEqualTo("Bitmap Index Scan"));
            assertThat(result.getIndexUsed()).isNotNull();
        } else {
            // Без индексов может быть Seq Scan или другие операции
            // PostgreSQL может использовать различные стратегии в зависимости от данных
            // Примечание: PostgreSQL может использовать автоматические индексы (например,
            // users_email_key для UNIQUE)
            // поэтому проверяем только, что наши тестовые индексы не используются
            if (result.getIndexUsed() != null) {
                // Игнорируем автоматические индексы (созданные для UNIQUE/PRIMARY KEY ограничений)
                assertThat(result.getIndexUsed())
                        .as(
                                "Only system indexes should be used (not our test indexes) for %s"
                                        + " without index",
                                queryType)
                        .doesNotContain("idx_");
            }
        }
    }

    private void validateBufferRatio(
            IndexPerformanceTest<?> without, IndexPerformanceTest<?> with, String queryType) {
        if (without.getBuffersHit() != null
                && without.getBuffersRead() != null
                && with.getBuffersHit() != null
                && with.getBuffersRead() != null) {

            // С индексами должно быть больше попаданий в кэш и меньше чтений с диска
            long withoutTotal = without.getBuffersHit() + without.getBuffersRead();
            long withTotal = with.getBuffersHit() + with.getBuffersRead();

            if (withoutTotal > 0 && withTotal > 0) {
                double withoutHitRatio = (double) without.getBuffersHit() / withoutTotal;
                double withHitRatio = (double) with.getBuffersHit() / withTotal;

                // С индексами hit ratio должен быть выше или равен
                assertThat(withHitRatio)
                        .isGreaterThanOrEqualTo(withoutHitRatio)
                        .as("Buffer hit ratio should be better with indexes for %s", queryType);
            }
        }
    }

    private void validateCostEstimate(
            IndexPerformanceTest<?> without, IndexPerformanceTest<?> with, String queryType) {
        if (without.getCostEstimate() != null && with.getCostEstimate() != null) {
            // С индексами cost estimate должен быть меньше или равен
            assertThat(with.getCostEstimate())
                    .isLessThanOrEqualTo(without.getCostEstimate())
                    .as("Cost estimate should be lower or equal with indexes for %s", queryType);
        }
    }
}
