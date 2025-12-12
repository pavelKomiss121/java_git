/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
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
import ru.mentee.power.batch.impl.BasicBatchProcessor;
import ru.mentee.power.batch.impl.OptimizedBatchProcessor;
import ru.mentee.power.batch.interfaces.BatchProcessor;
import ru.mentee.power.batch.model.BatchResult;
import ru.mentee.power.model.Product;

@Testcontainers
public class BatchProcessorPerformanceTest {

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
    @DisplayName("Should insert 100k records in under 10 seconds")
    void shouldInsertLargeDatasetEfficiently() throws Exception {
        // Given
        List<Product> products = generateProducts(100_000);
        BatchProcessor processor = new OptimizedBatchProcessor(connection);

        // When
        long startTime = System.currentTimeMillis();
        BatchResult result = processor.insert(products);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(result.getSuccessfulRecords()).isEqualTo(100_000);
        assertThat(duration).isLessThan(10_000); // менее 10 секунд
        assertThat(result.getRecordsPerSecond()).isGreaterThan(10_000);
    }

    @Test
    @DisplayName("Should compare BasicBatchProcessor vs OptimizedBatchProcessor performance")
    void shouldCompareBatchProcessorPerformance() throws Exception {
        // Given
        List<Product> products = generateProducts(10_000);

        // When - BasicBatchProcessor
        BasicBatchProcessor basicProcessor = new BasicBatchProcessor(connection);
        long basicStart = System.currentTimeMillis();
        BatchResult basicResult = basicProcessor.insert(products);
        long basicDuration = System.currentTimeMillis() - basicStart;

        // Очищаем таблицу (сначала зависимые таблицы)
        try (var stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM mentee_power.order_items");
            stmt.execute("DELETE FROM mentee_power.orders");
            stmt.execute("DELETE FROM mentee_power.products");
        }

        // When - OptimizedBatchProcessor
        OptimizedBatchProcessor optimizedProcessor = new OptimizedBatchProcessor(connection);
        long optimizedStart = System.currentTimeMillis();
        BatchResult optimizedResult = optimizedProcessor.insert(products);
        long optimizedDuration = System.currentTimeMillis() - optimizedStart;

        // Then
        assertThat(basicResult.getSuccessfulRecords()).isEqualTo(10_000);
        assertThat(optimizedResult.getSuccessfulRecords()).isEqualTo(10_000);

        System.out.printf(
                "BasicBatchProcessor: %d ms, %.2f rec/s%n",
                basicDuration, basicResult.getRecordsPerSecond());
        System.out.printf(
                "OptimizedBatchProcessor: %d ms, %.2f rec/s%n",
                optimizedDuration, optimizedResult.getRecordsPerSecond());
    }

    @Test
    @DisplayName("Should handle batch upsert operations efficiently")
    void shouldHandleBatchUpsertEfficiently() throws Exception {
        // Given
        List<Product> products = generateProducts(5_000);
        OptimizedBatchProcessor processor = new OptimizedBatchProcessor(connection);

        // When - First insert
        BatchResult firstResult = processor.upsert(products);

        // When - Second upsert (should update existing)
        List<Product> updatedProducts = generateProductsWithSameSku(products);
        BatchResult secondResult = processor.upsert(updatedProducts);

        // Then
        assertThat(firstResult.getSuccessfulRecords()).isEqualTo(5_000);
        assertThat(secondResult.getSuccessfulRecords()).isEqualTo(5_000);
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

    private List<Product> generateProductsWithSameSku(List<Product> original) {
        List<Product> products = new ArrayList<>();
        for (Product originalProduct : original) {
            products.add(
                    Product.builder()
                            .sku(originalProduct.getSku())
                            .name(originalProduct.getName() + " Updated")
                            .description(originalProduct.getDescription())
                            .price(originalProduct.getPrice().add(BigDecimal.ONE))
                            .categoryId(originalProduct.getCategoryId())
                            .build());
        }
        return products;
    }
}
