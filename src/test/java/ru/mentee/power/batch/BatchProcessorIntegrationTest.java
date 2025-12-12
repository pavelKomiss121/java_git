/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.mentee.power.batch.impl.AdaptiveBatchProcessor;
import ru.mentee.power.batch.impl.BasicBatchProcessor;
import ru.mentee.power.batch.impl.OptimizedBatchProcessor;
import ru.mentee.power.batch.impl.PostgresCopyProcessor;
import ru.mentee.power.batch.model.BatchResult;
import ru.mentee.power.model.Product;

@Testcontainers
public class BatchProcessorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("postgres")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        // Создаем отдельное соединение для Liquibase
        try (Connection liquibaseConn =
                DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            JdbcConnection jdbcConnection = new JdbcConnection(liquibaseConn);
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);

            try (Liquibase liquibase =
                    new Liquibase(
                            "db/migrations_161/changelog.yaml",
                            new ClassLoaderResourceAccessor(),
                            database)) {
                liquibase.update("dev,test");
            }
        }

        // Создаем соединение для тестов
        connection =
                DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        // Очищаем таблицу перед каждым тестом (сначала зависимые таблицы)
        try (var stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM mentee_power.order_items");
            stmt.execute("DELETE FROM mentee_power.orders");
            stmt.execute("DELETE FROM mentee_power.products");
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Should perform batch update operations")
    void shouldPerformBatchUpdate() throws Exception {
        // Given
        List<Product> products = generateProducts(100);
        BasicBatchProcessor processor = new BasicBatchProcessor(connection);
        processor.insert(products);

        // Получаем ID вставленных продуктов
        List<Long> productIds = getProductIds();
        assertThat(productIds).hasSize(100);

        // Обновляем продукты
        List<Product> updatedProducts = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            updatedProducts.add(
                    Product.builder()
                            .id(productIds.get(i))
                            .name("Updated Product " + i)
                            .description("Updated Description")
                            .price(BigDecimal.valueOf(200))
                            .categoryId(2L)
                            .build());
        }

        // When
        BatchResult result = processor.update(updatedProducts);

        // Then
        assertThat(result.getSuccessfulRecords()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should perform batch delete operations")
    void shouldPerformBatchDelete() throws Exception {
        // Given
        List<Product> products = generateProducts(100);
        BasicBatchProcessor processor = new BasicBatchProcessor(connection);
        processor.insert(products);

        List<Long> productIds = getProductIds();
        assertThat(productIds).hasSize(100);

        // When
        BatchResult result = processor.delete(productIds);

        // Then
        assertThat(result.getSuccessfulRecords()).isEqualTo(100);

        // Проверяем, что продукты удалены
        List<Long> remainingIds = getProductIds();
        assertThat(remainingIds).isEmpty();
    }

    @Test
    @DisplayName("Should use adaptive batch size optimization")
    void shouldUseAdaptiveBatchSize() throws Exception {
        // Given
        List<Product> products = generateProducts(5_000);
        AdaptiveBatchProcessor processor = new AdaptiveBatchProcessor(connection);

        // When
        BatchResult result = processor.insert(products);

        // Then
        assertThat(result.getSuccessfulRecords()).isEqualTo(5_000);
        assertThat(processor.getCurrentBatchSize()).isGreaterThan(0);
        assertThat(processor.getMetrics()).isNotEmpty();
    }

    @Test
    @DisplayName("Should use PostgreSQL COPY for maximum performance")
    void shouldUsePostgresCopy() throws Exception {
        // Given
        List<Product> products = generateProducts(10_000);
        PostgresCopyProcessor processor = new PostgresCopyProcessor(connection);

        // When
        long startTime = System.currentTimeMillis();
        BatchResult result = processor.insert(products);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(result.getSuccessfulRecords()).isEqualTo(10_000);
        assertThat(duration).isLessThan(5_000); // COPY должен быть очень быстрым
        assertThat(result.getRecordsPerSecond()).isGreaterThan(2_000);
    }

    @Test
    @DisplayName("Should compare all batch processors performance")
    void shouldCompareAllBatchProcessors() throws Exception {
        // Given
        List<Product> products = generateProducts(1_000);

        // Test BasicBatchProcessor
        try (var stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM mentee_power.order_items");
            stmt.execute("DELETE FROM mentee_power.orders");
            stmt.execute("DELETE FROM mentee_power.products");
        }
        BasicBatchProcessor basicProcessor = new BasicBatchProcessor(connection);
        long basicStart = System.currentTimeMillis();
        BatchResult basicResult = basicProcessor.insert(products);
        long basicDuration = System.currentTimeMillis() - basicStart;

        // Test OptimizedBatchProcessor
        try (var stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM mentee_power.order_items");
            stmt.execute("DELETE FROM mentee_power.orders");
            stmt.execute("DELETE FROM mentee_power.products");
        }
        OptimizedBatchProcessor optimizedProcessor = new OptimizedBatchProcessor(connection);
        long optimizedStart = System.currentTimeMillis();
        BatchResult optimizedResult = optimizedProcessor.insert(products);
        long optimizedDuration = System.currentTimeMillis() - optimizedStart;

        // Test PostgresCopyProcessor
        try (var stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM mentee_power.order_items");
            stmt.execute("DELETE FROM mentee_power.orders");
            stmt.execute("DELETE FROM mentee_power.products");
        }
        PostgresCopyProcessor copyProcessor = new PostgresCopyProcessor(connection);
        long copyStart = System.currentTimeMillis();
        BatchResult copyResult = copyProcessor.insert(products);
        long copyDuration = System.currentTimeMillis() - copyStart;

        // Then
        assertThat(basicResult.getSuccessfulRecords()).isEqualTo(1_000);
        assertThat(optimizedResult.getSuccessfulRecords()).isEqualTo(1_000);
        assertThat(copyResult.getSuccessfulRecords()).isEqualTo(1_000);

        System.out.printf(
                "BasicBatchProcessor: %d ms, %.2f rec/s%n",
                basicDuration, basicResult.getRecordsPerSecond());
        System.out.printf(
                "OptimizedBatchProcessor: %d ms, %.2f rec/s%n",
                optimizedDuration, optimizedResult.getRecordsPerSecond());
        System.out.printf(
                "PostgresCopyProcessor: %d ms, %.2f rec/s%n",
                copyDuration, copyResult.getRecordsPerSecond());
    }

    private List<Product> generateProducts(int count) {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            products.add(
                    Product.builder()
                            .sku("SKU-" + UUID.randomUUID().toString())
                            .name("Product " + i)
                            .description("Description for product " + i)
                            .price(BigDecimal.valueOf(100 + i % 1000))
                            .categoryId(1L)
                            .build());
        }
        return products;
    }

    private List<Long> getProductIds() throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (var stmt = connection.createStatement();
                var rs = stmt.executeQuery("SELECT id FROM mentee_power.products ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
        }
        return ids;
    }
}
