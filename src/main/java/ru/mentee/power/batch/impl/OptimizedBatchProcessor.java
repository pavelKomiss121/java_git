/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.batch.interfaces.BatchProcessor;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.BatchResult;
import ru.mentee.power.batch.model.DetailedBatchResult;
import ru.mentee.power.model.Product;

/**
 * Оптимизированный batch процессор с использованием специфичных для БД оптимизаций.
 */
@Slf4j
public class OptimizedBatchProcessor implements BatchProcessor {
    private static final String MULTI_VALUE_INSERT =
            "INSERT INTO mentee_power.products (sku, name, description, price, category_id) "
                    + "VALUES %s "
                    + "ON CONFLICT (sku) DO UPDATE SET "
                    + "name = EXCLUDED.name, "
                    + "price = EXCLUDED.price, "
                    + "description = EXCLUDED.description, "
                    + "category_id = EXCLUDED.category_id";

    private static final int BATCH_SIZE = 1000;
    private final Connection connection;

    public OptimizedBatchProcessor(Connection connection) {
        this.connection = connection;
    }

    @Override
    public <T> BatchResult insert(List<T> records) throws SQLException {
        return bulkUpsertProducts(records);
    }

    @Override
    public <T> BatchResult update(List<T> records) throws SQLException {
        return bulkUpsertProducts(records);
    }

    @Override
    public BatchResult delete(List<Long> ids) throws SQLException {
        BasicBatchProcessor basicProcessor = new BasicBatchProcessor(connection);
        return basicProcessor.delete(ids);
    }

    @Override
    public <T> BatchResult upsert(List<T> records) throws SQLException {
        return bulkUpsertProducts(records);
    }

    @Override
    public <T> DetailedBatchResult processWithDetails(List<T> records, BatchOperation operation)
            throws SQLException {
        ResilientBatchProcessor resilientProcessor = new ResilientBatchProcessor(connection);
        return resilientProcessor.insertWithErrorHandling(records, operation);
    }

    /**
     * Сверхбыстрая вставка с использованием multi-value INSERT.
     * Работает в 5-10 раз быстрее обычного batch.
     */
    public <T> BatchResult bulkUpsertProducts(List<T> products) throws SQLException {
        if (products.isEmpty()) {
            return BatchResult.empty();
        }

        long startTime = System.currentTimeMillis();
        int totalProcessed = 0;

        try {
            connection.setAutoCommit(false);

            for (int i = 0; i < products.size(); i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, products.size());
                List<T> batch = products.subList(i, endIndex);
                String sql = buildMultiValueInsert(batch);

                try (Statement stmt = connection.createStatement()) {
                    int affected = stmt.executeUpdate(sql);
                    totalProcessed += affected;
                }

                connection.commit();
                log.debug("Обработано {} записей из {}", endIndex, products.size());
            }

            long duration = System.currentTimeMillis() - startTime;
            return BatchResult.builder()
                    .totalRecords(products.size())
                    .successfulRecords(totalProcessed)
                    .failedRecords(products.size() - totalProcessed)
                    .executionTimeMs(duration)
                    .recordsPerSecond(calculateThroughput(totalProcessed, duration))
                    .build();
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Ошибка при bulk upsert", e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private <T> String buildMultiValueInsert(List<T> batch) {
        StringBuilder values = new StringBuilder();

        for (int i = 0; i < batch.size(); i++) {
            T record = batch.get(i);
            if (record instanceof Product product) {
                if (i > 0) values.append(",");

                values.append(
                        String.format(
                                "('%s', '%s', '%s', %s, %s)",
                                escapeSql(product.getSku()),
                                escapeSql(product.getName()),
                                escapeSql(product.getDescription()),
                                product.getPrice() != null ? product.getPrice() : "NULL",
                                product.getCategoryId() != null
                                        ? product.getCategoryId()
                                        : "NULL"));
            }
        }

        return String.format(MULTI_VALUE_INSERT, values.toString());
    }

    private String escapeSql(String value) {
        if (value == null) return "NULL";
        return value.replace("'", "''");
    }

    private double calculateThroughput(int records, long timeMs) {
        if (timeMs == 0) return 0;
        return (records * 1000.0) / timeMs;
    }
}
