/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.impl;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import ru.mentee.power.batch.interfaces.BatchProcessor;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.BatchResult;
import ru.mentee.power.batch.model.DetailedBatchResult;
import ru.mentee.power.model.Product;

/**
 * Сверхбыстрая загрузка данных через PostgreSQL COPY. В 10-50 раз быстрее обычного batch!
 */
@Slf4j
public class PostgresCopyProcessor implements BatchProcessor {
    private static final String COPY_SQL =
            "COPY mentee_power.products (sku, name, description, price, category_id) "
                    + "FROM STDIN WITH (FORMAT csv, HEADER false, DELIMITER ',')";

    private final Connection connection;

    public PostgresCopyProcessor(Connection connection) {
        this.connection = connection;
    }

    @Override
    public <T> BatchResult insert(List<T> records) throws SQLException {
        return loadWithCopy(records);
    }

    @Override
    public <T> BatchResult update(List<T> records) throws SQLException {
        BasicBatchProcessor basicProcessor = new BasicBatchProcessor(connection);
        return basicProcessor.update(records);
    }

    @Override
    public BatchResult delete(List<Long> ids) throws SQLException {
        BasicBatchProcessor basicProcessor = new BasicBatchProcessor(connection);
        return basicProcessor.delete(ids);
    }

    @Override
    public <T> BatchResult upsert(List<T> records) throws SQLException {
        return loadWithCopy(records);
    }

    @Override
    public <T> DetailedBatchResult processWithDetails(List<T> records, BatchOperation operation)
            throws SQLException {
        ResilientBatchProcessor resilientProcessor = new ResilientBatchProcessor(connection);
        return resilientProcessor.insertWithErrorHandling(records, operation);
    }

    /**
     * Загрузка через COPY - максимальная производительность для PostgreSQL.
     */
    public <T> BatchResult loadWithCopy(List<T> products) throws SQLException {
        if (!(connection instanceof BaseConnection)) {
            throw new IllegalStateException("Требуется PostgreSQL connection");
        }

        BaseConnection pgConnection = (BaseConnection) connection;
        CopyManager copyManager = new CopyManager(pgConnection);

        long startTime = System.currentTimeMillis();

        try {
            String csvData = convertToCsv(products);

            long rowsInserted = copyManager.copyIn(COPY_SQL, new StringReader(csvData));

            long duration = System.currentTimeMillis() - startTime;

            log.info("COPY загрузил {} записей за {} мс", rowsInserted, duration);

            return BatchResult.builder()
                    .totalRecords(products.size())
                    .successfulRecords((int) rowsInserted)
                    .failedRecords(products.size() - (int) rowsInserted)
                    .executionTimeMs(duration)
                    .recordsPerSecond(calculateThroughput((int) rowsInserted, duration))
                    .build();
        } catch (IOException e) {
            throw new SQLException("Ошибка при COPY операции", e);
        }
    }

    private <T> String convertToCsv(List<T> products) {
        StringBuilder csv = new StringBuilder();

        for (T record : products) {
            if (record instanceof Product product) {
                csv.append(escapeCsv(product.getSku()))
                        .append(",")
                        .append(escapeCsv(product.getName()))
                        .append(",")
                        .append(escapeCsv(product.getDescription()))
                        .append(",")
                        .append(product.getPrice() != null ? product.getPrice() : "")
                        .append(",")
                        .append(product.getCategoryId() != null ? product.getCategoryId() : "")
                        .append("\n");
            }
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private double calculateThroughput(int records, long timeMs) {
        if (timeMs == 0) return 0;
        return (records * 1000.0) / timeMs;
    }
}
