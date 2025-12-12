/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.jdbc.postgres;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.jdbc.interfaces.DatabaseSchemaAnalyzer;
import ru.mentee.power.model.mp173.SchemaInfo;
import ru.mentee.power.model.mp173.TableOptimizationInfo;
import ru.mentee.power.model.mp173.TableStatistics;

/**
 * Реализация анализатора схемы базы данных через DatabaseMetaData для PostgreSQL.
 */
public class PostgresDatabaseSchemaAnalyzer implements DatabaseSchemaAnalyzer {

    private static final String SCHEMA_NAME = "mentee_power";

    private final ApplicationConfig config;

    public PostgresDatabaseSchemaAnalyzer(ApplicationConfig config) {
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
    public SchemaInfo analyzeDatabaseSchema() throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData dbmd = conn.getMetaData();

            SchemaInfo info =
                    SchemaInfo.builder()
                            .databaseProductName(dbmd.getDatabaseProductName())
                            .databaseVersion(dbmd.getDatabaseProductVersion())
                            .driverName(dbmd.getDriverName())
                            .jdbcVersion(
                                    dbmd.getJDBCMajorVersion() + "." + dbmd.getJDBCMinorVersion())
                            .schemaName(SCHEMA_NAME)
                            .build();

            return info;
        }
    }

    @Override
    public List<TableOptimizationInfo> findMissingIndexes() throws SQLException {
        List<TableOptimizationInfo> optimizationInfos = new ArrayList<>();

        try (Connection conn = getConnection()) {
            DatabaseMetaData dbmd = conn.getMetaData();

            // Получаем все таблицы
            try (ResultSet tables =
                    dbmd.getTables(null, SCHEMA_NAME, "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");

                    // Получаем все внешние ключи для таблицы
                    List<String> foreignKeyColumns = new ArrayList<>();
                    try (ResultSet fks = dbmd.getImportedKeys(null, SCHEMA_NAME, tableName)) {
                        while (fks.next()) {
                            String fkColumnName = fks.getString("FKCOLUMN_NAME");
                            foreignKeyColumns.add(fkColumnName);
                        }
                    }

                    // Получаем все индексы для таблицы
                    List<String> indexedColumns = new ArrayList<>();
                    try (ResultSet indexes =
                            dbmd.getIndexInfo(null, SCHEMA_NAME, tableName, false, false)) {
                        while (indexes.next()) {
                            String indexColumnName = indexes.getString("COLUMN_NAME");
                            if (indexColumnName != null) {
                                indexedColumns.add(indexColumnName);
                            }
                        }
                    }

                    // Находим FK колонки без индексов
                    int foreignKeyCount = foreignKeyColumns.size();
                    int indexedForeignKeyCount = 0;
                    for (String fkColumn : foreignKeyColumns) {
                        if (indexedColumns.contains(fkColumn)) {
                            indexedForeignKeyCount++;
                        }
                    }

                    // Если есть FK без индексов, добавляем в список оптимизации
                    if (foreignKeyCount > 0 && indexedForeignKeyCount < foreignKeyCount) {
                        List<String> missingIndexColumns = new ArrayList<>();
                        for (String fkColumn : foreignKeyColumns) {
                            if (!indexedColumns.contains(fkColumn)) {
                                missingIndexColumns.add(fkColumn);
                            }
                        }

                        String recommendation =
                                String.format(
                                        "Рекомендуется создать индексы на колонках: %s",
                                        String.join(", ", missingIndexColumns));

                        TableOptimizationInfo info =
                                TableOptimizationInfo.builder()
                                        .schemaName(SCHEMA_NAME)
                                        .tableName(tableName)
                                        .foreignKeyCount(foreignKeyCount)
                                        .indexedForeignKeyCount(indexedForeignKeyCount)
                                        .recommendation(recommendation)
                                        .build();

                        optimizationInfos.add(info);
                    }
                }
            }
        }

        return optimizationInfos;
    }

    @Override
    public Map<String, TableStatistics> getTableStatistics() throws SQLException {
        Map<String, TableStatistics> statisticsMap = new HashMap<>();

        try (Connection conn = getConnection()) {
            // SQL запрос для получения статистики таблиц PostgreSQL
            String sql =
                    """
                    SELECT
                        schemaname,
                        tablename,
                        pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS total_size,
                        pg_total_relation_size(schemaname||'.'||tablename) AS total_size_bytes,
                        pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS table_size,
                        pg_relation_size(schemaname||'.'||tablename) AS table_size_bytes,
                        pg_size_pretty(pg_indexes_size(schemaname||'.'||tablename)) AS indexes_size,
                        pg_indexes_size(schemaname||'.'||tablename) AS indexes_size_bytes,
                        (SELECT COUNT(*) FROM pg_indexes WHERE schemaname = t.schemaname AND tablename = t.tablename) AS index_count,
                        (SELECT n_live_tup FROM pg_stat_user_tables WHERE schemaname = t.schemaname AND relname = t.tablename) AS row_count,
                        (SELECT seq_scan FROM pg_stat_user_tables WHERE schemaname = t.schemaname AND relname = t.tablename) AS seq_scan,
                        (SELECT idx_scan FROM pg_stat_user_tables WHERE schemaname = t.schemaname AND relname = t.tablename) AS idx_scan,
                        (SELECT n_dead_tup FROM pg_stat_user_tables WHERE schemaname = t.schemaname AND relname = t.tablename) AS dead_tuples,
                        (SELECT n_live_tup FROM pg_stat_user_tables WHERE schemaname = t.schemaname AND relname = t.tablename) AS live_tuples,
                        (SELECT last_vacuum FROM pg_stat_user_tables WHERE schemaname = t.schemaname AND relname = t.tablename) AS last_vacuum,
                        (SELECT last_analyze FROM pg_stat_user_tables WHERE schemaname = t.schemaname AND relname = t.tablename) AS last_analyze
                    FROM pg_tables t
                    WHERE schemaname = ?
                    ORDER BY tablename
                    """;

            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, SCHEMA_NAME);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String tableName = rs.getString("tablename");

                        Long indexesSizeBytes = rs.getLong("indexes_size_bytes");
                        if (rs.wasNull()) {
                            indexesSizeBytes = 0L;
                        }

                        Long totalSizeBytes = rs.getLong("total_size_bytes");
                        if (rs.wasNull()) {
                            totalSizeBytes = 0L;
                        }

                        Long rowCount = rs.getLong("row_count");
                        if (rs.wasNull()) {
                            rowCount = 0L;
                        }

                        Long seqScan = rs.getLong("seq_scan");
                        if (rs.wasNull()) {
                            seqScan = 0L;
                        }

                        Long idxScan = rs.getLong("idx_scan");
                        if (rs.wasNull()) {
                            idxScan = 0L;
                        }

                        Long deadTuples = rs.getLong("dead_tuples");
                        if (rs.wasNull()) {
                            deadTuples = 0L;
                        }

                        Long liveTuples = rs.getLong("live_tuples");
                        if (rs.wasNull()) {
                            liveTuples = 0L;
                        }

                        Integer indexCount = rs.getInt("index_count");
                        if (rs.wasNull()) {
                            indexCount = 0;
                        }

                        // Вычисляем эффективность индексов
                        Double indexEfficiency = null;
                        long totalScans = seqScan + idxScan;
                        if (totalScans > 0) {
                            indexEfficiency = (idxScan * 100.0) / totalScans;
                        }

                        java.sql.Timestamp lastVacuum = rs.getTimestamp("last_vacuum");
                        LocalDateTime vacuumLastRun =
                                lastVacuum != null ? lastVacuum.toLocalDateTime() : null;

                        java.sql.Timestamp lastAnalyze = rs.getTimestamp("last_analyze");
                        LocalDateTime analyzeLastRun =
                                lastAnalyze != null ? lastAnalyze.toLocalDateTime() : null;

                        TableStatistics stats =
                                TableStatistics.builder()
                                        .schemaName(SCHEMA_NAME)
                                        .tableName(tableName)
                                        .rowCount(rowCount)
                                        .tableSize(totalSizeBytes)
                                        .tableSizePretty(rs.getString("total_size"))
                                        .indexesSize(indexesSizeBytes)
                                        .indexesSizePretty(rs.getString("indexes_size"))
                                        .indexCount(indexCount)
                                        .indexEfficiency(indexEfficiency)
                                        .sequentialScans(seqScan)
                                        .indexScans(idxScan)
                                        .deadTuples(deadTuples)
                                        .liveTuples(liveTuples)
                                        .vacuumLastRun(vacuumLastRun)
                                        .analyzeLastRun(analyzeLastRun)
                                        .build();

                        statisticsMap.put(tableName, stats);
                    }
                }
            }
        }

        return statisticsMap;
    }
}
