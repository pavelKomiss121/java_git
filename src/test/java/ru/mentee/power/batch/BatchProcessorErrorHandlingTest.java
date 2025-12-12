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
import ru.mentee.power.batch.impl.ResilientBatchProcessor;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.DetailedBatchResult;
import ru.mentee.power.model.Product;

@Testcontainers
public class BatchProcessorErrorHandlingTest {

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
    @DisplayName("Should handle partial batch failures gracefully")
    void shouldHandlePartialBatchFailures() throws Exception {
        // Given - создаем продукты с дубликатами SKU
        List<Product> products = generateProductsWithDuplicates(1000);
        ResilientBatchProcessor processor = new ResilientBatchProcessor(connection);

        // When
        DetailedBatchResult result =
                processor.insertWithErrorHandling(products, BatchOperation.INSERT);

        // Then
        assertThat(result.getSuccessfulRecords()).isGreaterThan(900);
        assertThat(result.getFailedRecords()).isGreaterThan(0);
        assertThat(result.getFailedRecordsDetails()).isNotEmpty();

        // Проверяем, что ошибки связаны с дубликатами (код ошибки 23505 - unique violation)
        if (!result.getFailedRecordsDetails().isEmpty()) {
            int errorCode = result.getFailedRecordsDetails().get(0).getErrorCode();
            // PostgreSQL error code 23505 = unique_violation
            assertThat(errorCode).isEqualTo(23505);
        }
    }

    @Test
    @DisplayName("Should continue processing after encountering errors")
    void shouldContinueProcessingAfterErrors() throws Exception {
        // Given
        List<Product> products = new ArrayList<>();

        // Добавляем валидные продукты
        for (int i = 0; i < 500; i++) {
            products.add(
                    Product.builder()
                            .sku("VALID-SKU-" + UUID.randomUUID())
                            .name("Valid Product " + i)
                            .description("Description")
                            .price(BigDecimal.valueOf(100))
                            .categoryId(1L)
                            .build());
        }

        // Добавляем продукты с дубликатами SKU
        String duplicateSku = "DUPLICATE-SKU";
        for (int i = 0; i < 50; i++) {
            products.add(
                    Product.builder()
                            .sku(duplicateSku)
                            .name("Duplicate Product " + i)
                            .description("Description")
                            .price(BigDecimal.valueOf(100))
                            .categoryId(1L)
                            .build());
        }

        // Добавляем еще валидные продукты
        for (int i = 0; i < 450; i++) {
            products.add(
                    Product.builder()
                            .sku("VALID-SKU-2-" + UUID.randomUUID())
                            .name("Valid Product 2 " + i)
                            .description("Description")
                            .price(BigDecimal.valueOf(100))
                            .categoryId(1L)
                            .build());
        }

        ResilientBatchProcessor processor = new ResilientBatchProcessor(connection);

        // When
        DetailedBatchResult result =
                processor.insertWithErrorHandling(products, BatchOperation.INSERT);

        // Then
        // Должны быть обработаны валидные записи
        assertThat(result.getSuccessfulRecords()).isGreaterThan(900);
        // Должны быть ошибки для дубликатов
        assertThat(result.getFailedRecords()).isGreaterThan(0);
        // Общее количество должно совпадать
        assertThat(result.getTotalRecords()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should provide detailed error information for failed records")
    void shouldProvideDetailedErrorInformation() throws Exception {
        // Given
        List<Product> products = generateProductsWithDuplicates(100);

        // Сначала вставляем часть продуктов
        ResilientBatchProcessor basicProcessor = new ResilientBatchProcessor(connection);
        List<Product> firstBatch = products.subList(0, 50);
        basicProcessor.insertWithErrorHandling(firstBatch, BatchOperation.INSERT);

        // When - пытаемся вставить дубликаты
        ResilientBatchProcessor resilientProcessor = new ResilientBatchProcessor(connection);
        DetailedBatchResult result =
                resilientProcessor.insertWithErrorHandling(products, BatchOperation.INSERT);

        // Then
        assertThat(result.getFailedRecordsDetails()).isNotEmpty();
        assertThat(result.getFailedRecordsDetails().get(0).getErrorMessage()).isNotNull();
        assertThat(result.getFailedRecordsDetails().get(0).getData()).isNotNull();
    }

    private List<Product> generateProductsWithDuplicates(int count) {
        List<Product> products = new ArrayList<>();
        String duplicateSku = "DUPLICATE-SKU-" + UUID.randomUUID();

        for (int i = 0; i < count; i++) {
            // Каждый 20-й продукт будет иметь дублирующийся SKU
            String sku = (i % 20 == 0) ? duplicateSku : "SKU-" + UUID.randomUUID();
            products.add(
                    Product.builder()
                            .sku(sku)
                            .name("Product " + i)
                            .description("Description for product " + i)
                            .price(BigDecimal.valueOf(100 + i % 1000))
                            .categoryId(1L)
                            .build());
        }
        return products;
    }
}
