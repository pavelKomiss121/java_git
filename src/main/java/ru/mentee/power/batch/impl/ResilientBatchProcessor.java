/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.batch.model.BatchOperation;
import ru.mentee.power.batch.model.DetailedBatchResult;
import ru.mentee.power.batch.model.FailedRecord;
import ru.mentee.power.model.Product;

/**
 * Batch процессор с детальной обработкой ошибок и retry логикой.
 */
public class ResilientBatchProcessor {
    private static final String INSERT_PRODUCT =
            "INSERT INTO mentee_power.products (sku, name, description, price, category_id) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private final Connection connection;

    public ResilientBatchProcessor(Connection connection) {
        this.connection = connection;
    }

    /**
     * Batch вставка с обработкой частичных ошибок.
     * Вставляет записи по одной для обработки частичных ошибок.
     */
    public <T> DetailedBatchResult insertWithErrorHandling(
            List<T> records, BatchOperation operation) throws SQLException {
        DetailedBatchResult.DetailedBatchResultBuilder resultBuilder =
                DetailedBatchResult.builder()
                        .totalRecords(records.size())
                        .successfulRecords(0)
                        .failedRecords(0)
                        .executionTimeMs(0);

        long startTime = System.currentTimeMillis();
        boolean originalAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);

            for (int i = 0; i < records.size(); i++) {
                T record = records.get(i);
                if (record instanceof Product product) {
                    try (PreparedStatement ps = connection.prepareStatement(INSERT_PRODUCT)) {
                        setProductParameters(ps, product);
                        int updateCount = ps.executeUpdate();

                        if (updateCount > 0) {
                            resultBuilder.incrementSuccessful();
                            connection.commit();
                        } else {
                            resultBuilder.addFailedRecord(
                                    FailedRecord.builder()
                                            .index(i)
                                            .data(product)
                                            .errorCode(0)
                                            .errorMessage("No rows affected")
                                            .build());
                            connection.rollback();
                        }
                    } catch (SQLException e) {
                        // PostgreSQL использует SQLState для кодов ошибок, а не getErrorCode()
                        // SQLState "23505" = unique_violation
                        int errorCode = parsePostgresErrorCode(e);
                        resultBuilder.addFailedRecord(
                                FailedRecord.builder()
                                        .index(i)
                                        .data(product)
                                        .errorCode(errorCode)
                                        .errorMessage(e.getMessage())
                                        .build());
                        try {
                            connection.rollback();
                        } catch (SQLException rollbackEx) {
                            // Игнорируем ошибку rollback, так как основная ошибка уже обработана
                        }
                    }
                }
            }
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Сохраняем список failedRecordsDetails перед build(), так как builder можно использовать
        // только один раз
        // Создаем временный результат для получения всех данных
        DetailedBatchResult tempResult = resultBuilder.executionTimeMs(duration).build();

        double recordsPerSecond =
                tempResult.getSuccessfulRecords() > 0 && duration > 0
                        ? (tempResult.getSuccessfulRecords() * 1000.0) / duration
                        : 0.0;

        // Создаем финальный результат с сохраненным списком failedRecordsDetails
        DetailedBatchResult result =
                DetailedBatchResult.builder()
                        .totalRecords(tempResult.getTotalRecords())
                        .successfulRecords(tempResult.getSuccessfulRecords())
                        .failedRecords(tempResult.getFailedRecords())
                        .executionTimeMs(tempResult.getExecutionTimeMs())
                        .recordsPerSecond(recordsPerSecond)
                        .failedRecordsDetails(
                                new ArrayList<>(
                                        tempResult
                                                .getFailedRecordsDetails())) // Явно копируем список
                        .build();

        return result;
    }

    private void setProductParameters(PreparedStatement ps, Product product) throws SQLException {
        ps.setString(1, product.getSku());
        ps.setString(2, product.getName());
        ps.setString(3, product.getDescription());
        ps.setBigDecimal(4, product.getPrice());
        ps.setObject(5, product.getCategoryId());
    }

    /**
     * Парсит код ошибки PostgreSQL из SQLException.
     * PostgreSQL использует SQLState для кодов ошибок (например, "23505" для unique_violation).
     * Если SQLState не является числовым кодом, возвращает getErrorCode().
     */
    private int parsePostgresErrorCode(SQLException e) {
        String sqlState = e.getSQLState();
        if (sqlState != null && sqlState.matches("\\d{5}")) {
            try {
                return Integer.parseInt(sqlState);
            } catch (NumberFormatException ex) {
                // Если не удалось распарсить, используем getErrorCode()
                return e.getErrorCode();
            }
        }
        return e.getErrorCode();
    }
}
