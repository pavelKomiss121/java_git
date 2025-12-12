/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.batch.interfaces.BatchProcessor;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.BatchResult;
import ru.mentee.power.batch.model.DetailedBatchResult;
import ru.mentee.power.model.Product;

/**
 * Базовая реализация batch операций для массовой загрузки данных.
 */
@Slf4j
public class BasicBatchProcessor implements BatchProcessor {
    private static final String INSERT_PRODUCT =
            "INSERT INTO mentee_power.products (sku, name, description, price, category_id) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_PRODUCT =
            "UPDATE mentee_power.products SET name = ?, description = ?, price = ?, category_id = ?"
                    + " WHERE id = ?";
    private static final String DELETE_PRODUCT = "DELETE FROM mentee_power.products WHERE id = ?";
    private static final String UPSERT_PRODUCT =
            "INSERT INTO mentee_power.products (sku, name, description, price, category_id) "
                    + "VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (sku) DO UPDATE SET "
                    + "name = EXCLUDED.name, "
                    + "price = EXCLUDED.price, "
                    + "description = EXCLUDED.description, "
                    + "category_id = EXCLUDED.category_id";

    private static final int BATCH_SIZE = 1000;
    private final Connection connection;

    public BasicBatchProcessor(Connection connection) {
        this.connection = connection;
    }

    @Override
    public <T> BatchResult insert(List<T> records) throws SQLException {
        if (records.isEmpty()) {
            return BatchResult.empty();
        }

        long startTime = System.currentTimeMillis();
        int totalInserted = 0;

        try (PreparedStatement ps = connection.prepareStatement(INSERT_PRODUCT)) {
            connection.setAutoCommit(false);

            int count = 0;
            for (T record : records) {
                if (record instanceof Product product) {
                    setProductInsertParameters(ps, product);
                    ps.addBatch();
                    count++;

                    if (count % BATCH_SIZE == 0) {
                        int[] results = ps.executeBatch();
                        totalInserted += results.length;
                        connection.commit();
                        log.debug("Выполнен batch из {} записей", BATCH_SIZE);
                    }
                }
            }

            if (count % BATCH_SIZE != 0) {
                int[] results = ps.executeBatch();
                totalInserted += results.length;
                connection.commit();
            }

            long duration = System.currentTimeMillis() - startTime;
            return BatchResult.builder()
                    .totalRecords(records.size())
                    .successfulRecords(totalInserted)
                    .failedRecords(records.size() - totalInserted)
                    .executionTimeMs(duration)
                    .recordsPerSecond(calculateThroughput(totalInserted, duration))
                    .build();
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Ошибка при batch вставке продуктов", e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public <T> BatchResult update(List<T> records) throws SQLException {
        if (records.isEmpty()) {
            return BatchResult.empty();
        }

        long startTime = System.currentTimeMillis();
        int totalUpdated = 0;

        try (PreparedStatement ps = connection.prepareStatement(UPDATE_PRODUCT)) {
            connection.setAutoCommit(false);

            int count = 0;
            for (T record : records) {
                if (record instanceof Product product) {
                    setProductUpdateParameters(ps, product);
                    ps.addBatch();
                    count++;

                    if (count % BATCH_SIZE == 0) {
                        int[] results = ps.executeBatch();
                        totalUpdated += results.length;
                        connection.commit();
                    }
                }
            }

            if (count % BATCH_SIZE != 0) {
                int[] results = ps.executeBatch();
                totalUpdated += results.length;
                connection.commit();
            }

            long duration = System.currentTimeMillis() - startTime;
            return BatchResult.builder()
                    .totalRecords(records.size())
                    .successfulRecords(totalUpdated)
                    .failedRecords(records.size() - totalUpdated)
                    .executionTimeMs(duration)
                    .recordsPerSecond(calculateThroughput(totalUpdated, duration))
                    .build();
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Ошибка при batch обновлении продуктов", e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public BatchResult delete(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return BatchResult.empty();
        }

        long startTime = System.currentTimeMillis();
        int totalDeleted = 0;

        try (PreparedStatement ps = connection.prepareStatement(DELETE_PRODUCT)) {
            connection.setAutoCommit(false);

            int count = 0;
            for (Long id : ids) {
                ps.setLong(1, id);
                ps.addBatch();
                count++;

                if (count % BATCH_SIZE == 0) {
                    int[] results = ps.executeBatch();
                    totalDeleted += results.length;
                    connection.commit();
                }
            }

            if (count % BATCH_SIZE != 0) {
                int[] results = ps.executeBatch();
                totalDeleted += results.length;
                connection.commit();
            }

            long duration = System.currentTimeMillis() - startTime;
            return BatchResult.builder()
                    .totalRecords(ids.size())
                    .successfulRecords(totalDeleted)
                    .failedRecords(ids.size() - totalDeleted)
                    .executionTimeMs(duration)
                    .recordsPerSecond(calculateThroughput(totalDeleted, duration))
                    .build();
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Ошибка при batch удалении продуктов", e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public <T> BatchResult upsert(List<T> records) throws SQLException {
        if (records.isEmpty()) {
            return BatchResult.empty();
        }

        long startTime = System.currentTimeMillis();
        int totalProcessed = 0;

        try (PreparedStatement ps = connection.prepareStatement(UPSERT_PRODUCT)) {
            connection.setAutoCommit(false);

            int count = 0;
            for (T record : records) {
                if (record instanceof Product product) {
                    setProductInsertParameters(ps, product);
                    ps.addBatch();
                    count++;

                    if (count % BATCH_SIZE == 0) {
                        int[] results = ps.executeBatch();
                        totalProcessed += results.length;
                        connection.commit();
                    }
                }
            }

            if (count % BATCH_SIZE != 0) {
                int[] results = ps.executeBatch();
                totalProcessed += results.length;
                connection.commit();
            }

            long duration = System.currentTimeMillis() - startTime;
            return BatchResult.builder()
                    .totalRecords(records.size())
                    .successfulRecords(totalProcessed)
                    .failedRecords(records.size() - totalProcessed)
                    .executionTimeMs(duration)
                    .recordsPerSecond(calculateThroughput(totalProcessed, duration))
                    .build();
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Ошибка при batch upsert продуктов", e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public <T> DetailedBatchResult processWithDetails(List<T> records, BatchOperation operation)
            throws SQLException {
        // Делегируем ResilientBatchProcessor
        ResilientBatchProcessor resilientProcessor = new ResilientBatchProcessor(connection);
        return resilientProcessor.insertWithErrorHandling(records, operation);
    }

    private void setProductInsertParameters(PreparedStatement ps, Product product)
            throws SQLException {
        ps.setString(1, product.getSku());
        ps.setString(2, product.getName());
        ps.setString(3, product.getDescription());
        ps.setBigDecimal(4, product.getPrice());
        ps.setObject(5, product.getCategoryId());
    }

    private void setProductUpdateParameters(PreparedStatement ps, Product product)
            throws SQLException {
        ps.setString(1, product.getName());
        ps.setString(2, product.getDescription());
        ps.setBigDecimal(3, product.getPrice());
        ps.setObject(4, product.getCategoryId());
        ps.setLong(5, product.getId());
    }

    private double calculateThroughput(int records, long timeMs) {
        if (timeMs == 0) return 0;
        return (records * 1000.0) / timeMs;
    }
}
