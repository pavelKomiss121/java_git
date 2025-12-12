/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.jdbc.postgres;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.jdbc.interfaces.StoredProcedureProcessor;
import ru.mentee.power.model.mp173.BatchProcedureResult;
import ru.mentee.power.model.mp173.ResultSetProcessor;
import ru.mentee.power.model.mp173.SearchCriteria;
import ru.mentee.power.model.mp173.UpdateRequest;
import ru.mentee.power.model.mp173.UserStatistics;

/**
 * Реализация процессора хранимых процедур через CallableStatement для PostgreSQL.
 */
public class PostgresStoredProcedureProcessor implements StoredProcedureProcessor {

    private static final String CALL_USER_STATS = "{CALL calculate_user_statistics(?, ?, ?, ?)}";
    private static final String CALL_BATCH_UPDATE = "{CALL batch_update_records(?, ?, ?)}";
    private static final String CALL_LARGE_RESULT_SET = "{CALL get_large_result_set(?, ?)}";

    private final ApplicationConfig config;

    public PostgresStoredProcedureProcessor(ApplicationConfig config) {
        this.config = config;
    }

    /**
     * Получить соединение с базой данных.
     */
    private Connection getConnection() throws SQLException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (var statement = conn.prepareStatement("SET search_path TO mentee_power, public")) {
            statement.execute();
        } catch (SQLException e) {
            conn.close();
            throw new SQLException("Ошибка установки search_path", e);
        }
        return conn;
    }

    @Override
    public UserStatistics calculateUserStatistics(Long userId) throws SQLException {
        try (Connection conn = getConnection();
                CallableStatement cs = conn.prepareCall(CALL_USER_STATS)) {

            // Установка входных параметров
            cs.setLong(1, userId);

            // Регистрация выходных параметров
            cs.registerOutParameter(2, Types.INTEGER);
            cs.registerOutParameter(3, Types.DECIMAL);
            cs.registerOutParameter(4, Types.DECIMAL);

            // Выполнение
            cs.execute();

            // Получение результатов
            return UserStatistics.builder()
                    .userId(userId)
                    .totalOrders(cs.getInt(2))
                    .totalSpent(cs.getBigDecimal(3))
                    .avgOrderValue(cs.getBigDecimal(4))
                    .build();
        }
    }

    @Override
    public BatchProcedureResult executeBatchProcedure(List<UpdateRequest> updates)
            throws SQLException {
        BatchProcedureResult result =
                BatchProcedureResult.builder()
                        .processedCount(0)
                        .successCount(0)
                        .failedCount(0)
                        .batchResults(new ArrayList<>())
                        .errors(new ArrayList<>())
                        .build();

        long startTime = System.currentTimeMillis();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            if (conn.getMetaData().supportsBatchUpdates()) {
                try (CallableStatement cs = conn.prepareCall(CALL_BATCH_UPDATE)) {
                    for (UpdateRequest update : updates) {
                        try {
                            cs.setLong(1, update.getRecordId());
                            cs.setString(2, update.getTableName());
                            // Предполагаем, что параметры передаются как JSON или строка
                            cs.setString(
                                    3,
                                    update.getUpdateParams() != null
                                            ? update.getUpdateParams().toString()
                                            : "");

                            cs.addBatch();
                            result.setProcessedCount(result.getProcessedCount() + 1);

                            // Выполняем пакетами по 1000
                            if (result.getProcessedCount() % 1000 == 0) {
                                int[] batchResults = cs.executeBatch();
                                for (int batchResult : batchResults) {
                                    result.addBatchResult(batchResult);
                                    if (batchResult > 0) {
                                        result.setSuccessCount(result.getSuccessCount() + 1);
                                    } else {
                                        result.setFailedCount(result.getFailedCount() + 1);
                                    }
                                }
                                cs.clearBatch();
                            }
                        } catch (SQLException e) {
                            result.addError(
                                    "Ошибка обработки запроса для recordId="
                                            + update.getRecordId()
                                            + ": "
                                            + e.getMessage());
                            result.setFailedCount(result.getFailedCount() + 1);
                        }
                    }

                    // Выполнить оставшиеся
                    if (result.getProcessedCount() % 1000 != 0) {
                        int[] batchResults = cs.executeBatch();
                        for (int batchResult : batchResults) {
                            result.addBatchResult(batchResult);
                            if (batchResult > 0) {
                                result.setSuccessCount(result.getSuccessCount() + 1);
                            } else {
                                result.setFailedCount(result.getFailedCount() + 1);
                            }
                        }
                    }

                    conn.commit();
                    result.setSuccess(true);
                }
            } else {
                throw new SQLException("Batch updates не поддерживаются");
            }
        } catch (SQLException e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);
            if (executionTime > 0 && result.getProcessedCount() != null) {
                result.setRecordsPerSecond((result.getProcessedCount() * 1000.0) / executionTime);
            }
        }

        return result;
    }

    @Override
    public ResultSetProcessor getLargeResultSet(SearchCriteria criteria) throws SQLException {
        Connection conn = getConnection();
        try {
            CallableStatement cs = conn.prepareCall(CALL_LARGE_RESULT_SET);

            // Установка параметров поиска
            if (criteria.getSearchText() != null) {
                cs.setString(1, criteria.getSearchText());
            } else {
                cs.setNull(1, Types.VARCHAR);
            }

            // Для REFCURSOR в PostgreSQL используется специальный тип
            cs.registerOutParameter(2, Types.OTHER);

            cs.execute();

            // Получение REFCURSOR
            ResultSet rs = (ResultSet) cs.getObject(2);

            return ResultSetProcessor.builder()
                    .resultSet(rs)
                    .cursorName("large_result_cursor")
                    .processedRows(0)
                    .hasMore(true)
                    .build();
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
    }
}
