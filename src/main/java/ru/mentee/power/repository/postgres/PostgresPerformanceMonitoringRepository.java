/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp164.*;
import ru.mentee.power.repository.interfaces.PerformanceMonitoringRepository;

public class PostgresPerformanceMonitoringRepository implements PerformanceMonitoringRepository {

    private static final String INDEX_USAGE_STATISTICS =
            """
            SELECT
                ind.schemaname as schema_name,
                ind.relname as table_name,
                ind.indexrelname as index_name,
                pg_relation_size(stat.indexrelid) as index_size,
                ind.idx_scan as index_scans,
                ind.idx_tup_read as tuples_read,
                ind.idx_tup_fetch as tuples_returned,
                CASE
                    WHEN ind.idx_tup_read > 0
                    THEN ROUND((ind.idx_tup_fetch::numeric / ind.idx_tup_read) * 100, 2)
                    ELSE NULL
                END as hit_ratio,
                am.amname as index_type,
                pi.indexdef as index_definition,
                stat.indisunique as is_unique,
                stat.indisprimary as is_primary
            FROM pg_index as stat
            LEFT JOIN pg_stat_user_indexes as ind
                ON stat.indexrelid = ind.indexrelid
            LEFT JOIN pg_class pc
                ON pc.oid = stat.indexrelid
            LEFT JOIN pg_am am
                ON am.oid = pc.relam
            LEFT JOIN pg_indexes pi
                ON pi.schemaname = ind.schemaname
                AND pi.indexname = ind.indexrelname
            WHERE ind.schemaname = 'mentee_power'
            ORDER BY ind.idx_scan DESC;
        """;

    private static final String UNUSED_INDEXES_QUERY =
            """
            SELECT
                psui.schemaname as schema_name,
                psui.relname as table_name,
                psui.indexrelname as index_name,
                pg_relation_size(psui.indexrelid) as size_bytes,
                pg_size_pretty(pg_relation_size(psui.indexrelid)) as size_pretty,
                NULL as last_used,
                NULL as days_unused,
                CASE
                    WHEN psui.idx_scan = 0 AND NOT idx.indisprimary AND NOT idx.indisunique
                    THEN 'Кандидат на удаление: индекс не используется'
                    WHEN psui.idx_scan < 10 AND NOT idx.indisprimary AND NOT idx.indisunique
                    THEN 'Редко используемый индекс: рассмотреть удаление'
                    ELSE 'Индекс используется, не удалять'
                END as recommendation,
                CASE
                    WHEN pg_relation_size(psui.indexrelid) > 1024 * 1024 * 100
                    THEN 'Высокий'
                    WHEN pg_relation_size(psui.indexrelid) > 1024 * 1024 * 10
                    THEN 'Средний'
                    ELSE 'Низкий'
                END as maintenance_cost
            FROM pg_stat_user_indexes psui
            INNER JOIN pg_index idx
                ON idx.indexrelid = psui.indexrelid
            WHERE psui.schemaname = 'mentee_power'
                AND (psui.idx_scan = 0 OR psui.idx_scan < 10)
                AND NOT idx.indisprimary
                AND NOT idx.indisunique
            ORDER BY pg_relation_size(psui.indexrelid) DESC;
        """;

    private static final String SLOW_QUERIES_QUERY =
            """
            SELECT
                md5(pss.query) as query_id,
                pss.query,
                pss.mean_exec_time as mean_execution_time,
                pss.total_exec_time as total_execution_time,
                pss.calls,
                pss.min_exec_time as min_time,
                pss.max_exec_time as max_time,
                ROW_NUMBER() OVER (ORDER BY pss.mean_exec_time DESC) as slow_query_rank
            FROM pg_stat_statements pss
            WHERE pss.query NOT LIKE '%pg_stat_statements%'
                AND pss.query NOT LIKE '%pg_catalog%'
                AND pss.mean_exec_time > 100
            ORDER BY pss.mean_exec_time DESC
            LIMIT 20;
        """;

    private static final String TABLE_STATISTICS_QUERY =
            """
            SELECT
                psut.schemaname as schema_name,
                psut.relname as table_name,
                psut.n_live_tup as row_count,
                pg_relation_size(psut.relid) as table_size,
                pg_size_pretty(pg_relation_size(psut.relid)) as table_size_pretty,
                (SELECT COALESCE(SUM(pg_relation_size(indexrelid)), 0)
                 FROM pg_stat_user_indexes
                 WHERE schemaname = psut.schemaname AND relname = psut.relname) as indexes_size,
                (SELECT pg_size_pretty(COALESCE(SUM(pg_relation_size(indexrelid)), 0))
                 FROM pg_stat_user_indexes
                 WHERE schemaname = psut.schemaname AND relname = psut.relname) as indexes_size_pretty,
                (SELECT COUNT(*)
                 FROM pg_stat_user_indexes
                 WHERE schemaname = psut.schemaname AND relname = psut.relname) as index_count,
                CASE
                    WHEN psut.seq_scan + psut.idx_scan > 0
                    THEN ROUND((psut.idx_scan::numeric / (psut.seq_scan + psut.idx_scan)) * 100, 2)
                    ELSE NULL
                END as index_efficiency,
                psut.seq_scan as sequential_scans,
                psut.idx_scan as index_scans,
                psut.last_vacuum as vacuum_last_run,
                psut.last_analyze as analyze_last_run
            FROM pg_stat_user_tables psut
            WHERE psut.schemaname = 'mentee_power'
            ORDER BY pg_relation_size(psut.relid) DESC;
        """;

    private static final String ACTIVE_QUERIES_QUERY =
            """
            SELECT
                psa.pid,
                psa.datname as database_name,
                psa.usename as user_name,
                COALESCE(psa.client_addr::text, '') as client_address,
                psa.query_start as query_start,
                psa.state,
                psa.query,
                CASE
                    WHEN psa.query_start IS NOT NULL
                    THEN EXTRACT(EPOCH FROM (NOW() - psa.query_start))
                    ELSE NULL
                END as duration,
                EXISTS(SELECT 1 FROM pg_locks WHERE pid = psa.pid AND granted = false) as is_blocking,
                NULL as blocked_by
            FROM pg_stat_activity psa
            WHERE psa.state != 'idle'
                AND psa.query NOT LIKE '%pg_stat_activity%'
                AND psa.datname = current_database()
            ORDER BY psa.query_start;
        """;

    private static final String CACHE_HIT_RATIO_QUERY =
            """
            SELECT
                SUM(heap_blks_hit) + SUM(idx_blks_hit) as buffer_hits,
                SUM(heap_blks_read) + SUM(idx_blks_read) as disk_reads
            FROM pg_statio_user_tables
            WHERE schemaname = 'mentee_power';
        """;

    private static final String TABLE_CACHE_STATS_QUERY =
            """
            SELECT
                relname as table_name,
                CASE
                    WHEN heap_blks_hit + heap_blks_read > 0
                    THEN ROUND((heap_blks_hit::numeric / (heap_blks_hit + heap_blks_read)) * 100, 2)
                    ELSE NULL
                END as hit_ratio,
                heap_blks_read,
                heap_blks_hit,
                idx_blks_read,
                idx_blks_hit
            FROM pg_statio_user_tables
            WHERE schemaname = 'mentee_power'
            ORDER BY heap_blks_read + idx_blks_read DESC;
        """;

    private static final String EXPLAIN_ANALYZE_WRAPPER =
            """
      EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) %s
      """;

    private ApplicationConfig config;

    public PostgresPerformanceMonitoringRepository(ApplicationConfig config) {
        this.config = config;
    }

    protected Connection getConnection() throws DataAccessException, SQLException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (PreparedStatement statement =
                conn.prepareStatement("SET search_path TO mentee_power, public")) {
            statement.execute();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка соединения", e);
        }
        return conn;
    }

    @Override
    public List<IndexUsageStats> getIndexUsageStatistics() throws DataAccessException {
        List<IndexUsageStats> result = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(INDEX_USAGE_STATISTICS);
                ResultSet resultSet = statement.executeQuery(); ) {
            while (resultSet.next()) {
                IndexUsageStats stats =
                        IndexUsageStats.builder()
                                .indexDefinition(resultSet.getString("index_definition"))
                                .indexScans(resultSet.getLong("index_scans"))
                                .indexSize(resultSet.getLong("index_size"))
                                .indexType(resultSet.getString("index_type"))
                                .hitRatio(resultSet.getDouble("hit_ratio"))
                                .tuplesReturned(resultSet.getLong("tuples_returned"))
                                .tuplesRead(resultSet.getLong("tuples_read"))
                                .tableName(resultSet.getString("table_name"))
                                .isUnique(resultSet.getBoolean("is_unique"))
                                .isPrimary(resultSet.getBoolean("is_primary"))
                                .schemaName(resultSet.getString("schema_name"))
                                .indexName(resultSet.getString("index_name"))
                                .build();
                result.add(stats);
            }
        } catch (SQLException ex) {
            throw new DataAccessException(
                    "Ошибка анализа статистики использования всех индексов", ex);
        }
        return result;
    }

    @Override
    public List<UnusedIndexReport> getUnusedIndexes() throws DataAccessException {
        List<UnusedIndexReport> result = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(UNUSED_INDEXES_QUERY);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Timestamp lastUsedTs = resultSet.getTimestamp("last_used");
                LocalDateTime lastUsed = lastUsedTs != null ? lastUsedTs.toLocalDateTime() : null;

                Double daysUnusedDouble = getDoubleOrNull(resultSet, "days_unused");
                Long daysUnused = daysUnusedDouble != null ? daysUnusedDouble.longValue() : null;

                UnusedIndexReport report =
                        UnusedIndexReport.builder()
                                .schemaName(resultSet.getString("schema_name"))
                                .tableName(resultSet.getString("table_name"))
                                .indexName(resultSet.getString("index_name"))
                                .sizeBytes(resultSet.getLong("size_bytes"))
                                .sizePretty(resultSet.getString("size_pretty"))
                                .lastUsed(lastUsed)
                                .daysUnused(daysUnused)
                                .recommendation(resultSet.getString("recommendation"))
                                .maintenanceCost(resultSet.getString("maintenance_cost"))
                                .build();
                result.add(report);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка получения неиспользуемых индексов", ex);
        }
        return result;
    }

    @Override
    public List<SlowQueryReport> getSlowQueriesWithRecommendations() throws DataAccessException {
        List<SlowQueryReport> result = new ArrayList<>();

        try (Connection conn = getConnection()) {
            // Проверяем доступность расширения pg_stat_statements
            if (!isPgStatStatementsAvailable(conn)) {
                // Возвращаем пустой список, если расширение недоступно
                return result;
            }

            try (PreparedStatement statement = conn.prepareStatement(SLOW_QUERIES_QUERY);
                    ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String query = resultSet.getString("query");
                    List<String> recommendedIndexes = analyzeQueryForIndexes(query);

                    SlowQueryReport report =
                            SlowQueryReport.builder()
                                    .queryId(resultSet.getString("query_id"))
                                    .query(query)
                                    .meanExecutionTime(
                                            resultSet.getBigDecimal("mean_execution_time"))
                                    .totalExecutionTime(
                                            resultSet.getBigDecimal("total_execution_time"))
                                    .calls(resultSet.getLong("calls"))
                                    .minTime(resultSet.getBigDecimal("min_time"))
                                    .maxTime(resultSet.getBigDecimal("max_time"))
                                    .recommendedIndexes(recommendedIndexes)
                                    .optimizationSuggestions(
                                            generateQueryOptimizationSuggestions(
                                                    query, recommendedIndexes))
                                    .slowQueryRank(resultSet.getInt("slow_query_rank"))
                                    .build();
                    result.add(report);
                }
            }
        } catch (SQLException ex) {
            // Если ошибка связана с отсутствием pg_stat_statements, возвращаем пустой список
            if (ex.getMessage() != null && ex.getMessage().contains("pg_stat_statements")) {
                return result;
            }
            throw new DataAccessException("Ошибка получения медленных запросов", ex);
        }
        return result;
    }

    /**
     * Проверяет доступность расширения pg_stat_statements.
     */
    private boolean isPgStatStatementsAvailable(Connection conn) {
        try (Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname ="
                                        + " 'pg_stat_statements')")) {
            if (rs.next()) {
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            // Если не удалось проверить, считаем что расширение недоступно
            return false;
        }
        return false;
    }

    @Override
    public QueryExecutionPlan getQueryExecutionPlan(String query) throws DataAccessException {
        try (Connection conn = getConnection()) {
            clearPlanCache(conn);

            String explainQuery = String.format(EXPLAIN_ANALYZE_WRAPPER, query);
            try (PreparedStatement explainStmt = conn.prepareStatement(explainQuery);
                    ResultSet explainRs = explainStmt.executeQuery()) {

                if (explainRs.next()) {
                    String queryPlan = explainRs.getString(1);
                    return parseExecutionPlan(query, queryPlan);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка получения плана выполнения запроса", ex);
        }
        return QueryExecutionPlan.builder().originalQuery(query).usesIndexes(false).build();
    }

    @Override
    public List<TableStatistics> getTableStatistics() throws DataAccessException {
        List<TableStatistics> result = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(TABLE_STATISTICS_QUERY);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Timestamp vacuumTs = resultSet.getTimestamp("vacuum_last_run");
                LocalDateTime vacuumLastRun = vacuumTs != null ? vacuumTs.toLocalDateTime() : null;

                Timestamp analyzeTs = resultSet.getTimestamp("analyze_last_run");
                LocalDateTime analyzeLastRun =
                        analyzeTs != null ? analyzeTs.toLocalDateTime() : null;

                TableStatistics stats =
                        TableStatistics.builder()
                                .schemaName(resultSet.getString("schema_name"))
                                .tableName(resultSet.getString("table_name"))
                                .rowCount(resultSet.getLong("row_count"))
                                .tableSize(resultSet.getLong("table_size"))
                                .tableSizePretty(resultSet.getString("table_size_pretty"))
                                .indexesSize(resultSet.getLong("indexes_size"))
                                .indexesSizePretty(resultSet.getString("indexes_size_pretty"))
                                .indexCount(resultSet.getInt("index_count"))
                                .indexEfficiency(getDoubleOrNull(resultSet, "index_efficiency"))
                                .sequentialScans(resultSet.getLong("sequential_scans"))
                                .indexScans(resultSet.getLong("index_scans"))
                                .vacuumLastRun(vacuumLastRun)
                                .analyzeLastRun(analyzeLastRun)
                                .build();
                result.add(stats);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка получения статистики таблиц", ex);
        }
        return result;
    }

    @Override
    public List<ActiveQueryInfo> getCurrentActiveQueries() throws DataAccessException {
        List<ActiveQueryInfo> result = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(ACTIVE_QUERIES_QUERY);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Timestamp queryStartTs = resultSet.getTimestamp("query_start");
                LocalDateTime queryStart =
                        queryStartTs != null ? queryStartTs.toLocalDateTime() : null;

                // blocked_by может быть NULL или массивом, обрабатываем соответствующим образом
                List<Integer> blockedBy = null;
                try {
                    Array blockedByArray = resultSet.getArray("blocked_by");
                    if (blockedByArray != null) {
                        Object[] blockedByObj = (Object[]) blockedByArray.getArray();
                        blockedBy =
                                Arrays.stream(blockedByObj)
                                        .map(
                                                obj ->
                                                        obj instanceof Number
                                                                ? ((Number) obj).intValue()
                                                                : null)
                                        .filter(java.util.Objects::nonNull)
                                        .collect(java.util.stream.Collectors.toList());
                    }
                } catch (SQLException e) {
                    // Если не удалось получить массив, оставляем null
                    blockedBy = null;
                }

                ActiveQueryInfo info =
                        ActiveQueryInfo.builder()
                                .pid(resultSet.getInt("pid"))
                                .databaseName(resultSet.getString("database_name"))
                                .userName(resultSet.getString("user_name"))
                                .clientAddress(resultSet.getString("client_address"))
                                .queryStart(queryStart)
                                .state(resultSet.getString("state"))
                                .query(resultSet.getString("query"))
                                .duration(resultSet.getBigDecimal("duration"))
                                .isBlocking(resultSet.getBoolean("is_blocking"))
                                .blockedBy(blockedBy)
                                .build();
                result.add(info);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка получения активных запросов", ex);
        }
        return result;
    }

    @Override
    public CacheHitRatioReport getCacheHitRatioReport() throws DataAccessException {
        try (Connection conn = getConnection()) {
            // Получаем общую статистику кэша
            long bufferHits = 0;
            long diskReads = 0;

            try (PreparedStatement statement = conn.prepareStatement(CACHE_HIT_RATIO_QUERY);
                    ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    bufferHits = resultSet.getLong("buffer_hits");
                    diskReads = resultSet.getLong("disk_reads");
                }
            }

            // Получаем статистику по таблицам
            List<TableCacheStats> tableStats = new ArrayList<>();
            try (PreparedStatement statement = conn.prepareStatement(TABLE_CACHE_STATS_QUERY);
                    ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TableCacheStats stats =
                            TableCacheStats.builder()
                                    .tableName(resultSet.getString("table_name"))
                                    .hitRatio(getDoubleOrNull(resultSet, "hit_ratio"))
                                    .heapBlksRead(resultSet.getLong("heap_blks_read"))
                                    .heapBlksHit(resultSet.getLong("heap_blks_hit"))
                                    .idxBlksRead(resultSet.getLong("idx_blks_read"))
                                    .idxBlksHit(resultSet.getLong("idx_blks_hit"))
                                    .build();
                    tableStats.add(stats);
                }
            }

            // Вычисляем общий hit ratio
            double overallHitRatio = 0.0;
            double indexHitRatio = 0.0;
            double tableHitRatio = 0.0;

            if (bufferHits + diskReads > 0) {
                overallHitRatio = (bufferHits * 100.0) / (bufferHits + diskReads);
            }

            long totalIndexHits =
                    tableStats.stream().mapToLong(TableCacheStats::getIdxBlksHit).sum();
            long totalIndexReads =
                    tableStats.stream().mapToLong(TableCacheStats::getIdxBlksRead).sum();
            if (totalIndexHits + totalIndexReads > 0) {
                indexHitRatio = (totalIndexHits * 100.0) / (totalIndexHits + totalIndexReads);
            }

            long totalHeapHits =
                    tableStats.stream().mapToLong(TableCacheStats::getHeapBlksHit).sum();
            long totalHeapReads =
                    tableStats.stream().mapToLong(TableCacheStats::getHeapBlksRead).sum();
            if (totalHeapHits + totalHeapReads > 0) {
                tableHitRatio = (totalHeapHits * 100.0) / (totalHeapHits + totalHeapReads);
            }

            String recommendation =
                    generateCacheRecommendation(overallHitRatio, indexHitRatio, tableHitRatio);

            return CacheHitRatioReport.builder()
                    .overallHitRatio(overallHitRatio)
                    .indexHitRatio(indexHitRatio)
                    .tableHitRatio(tableHitRatio)
                    .bufferHits(bufferHits)
                    .diskReads(diskReads)
                    .recommendation(recommendation)
                    .tableStats(tableStats)
                    .build();
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка получения отчета по кэш hit ratio", ex);
        }
    }

    @Override
    public IndexCreationResult createIndexSafely(
            String indexName, String tableName, String columns, String indexType)
            throws DataAccessException {
        LocalDateTime creationTime = LocalDateTime.now();
        String indexTypeStr = indexType != null && !indexType.isEmpty() ? indexType : "btree";

        try (Connection conn = getConnection()) {
            // Проверяем, существует ли индекс
            try (PreparedStatement checkStmt =
                    conn.prepareStatement(
                            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'mentee_power' AND"
                                    + " indexname = ?")) {
                checkStmt.setString(1, indexName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return IndexCreationResult.builder()
                                .indexName(indexName)
                                .success(false)
                                .creationTime(creationTime)
                                .errorMessage("Индекс уже существует")
                                .recommendations(
                                        "Используйте DROP INDEX перед созданием нового индекса")
                                .build();
                    }
                }
            }

            // Проверяем, находится ли соединение в транзакции
            // CREATE INDEX CONCURRENTLY не работает в транзакции
            boolean inTransaction = !conn.getAutoCommit();
            String createIndexSql;
            if (inTransaction) {
                // Используем обычный CREATE INDEX для транзакций
                createIndexSql =
                        String.format(
                                "CREATE INDEX %s ON mentee_power.%s USING %s (%s)",
                                indexName, tableName, indexTypeStr, columns);
            } else {
                // Используем CONCURRENTLY вне транзакции
                createIndexSql =
                        String.format(
                                "CREATE INDEX CONCURRENTLY %s ON mentee_power.%s USING %s (%s)",
                                indexName, tableName, indexTypeStr, columns);
            }

            // Измеряем время создания
            long startTime = System.currentTimeMillis();
            boolean success = false;
            String errorMessage = null;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createIndexSql);
                success = true;
            } catch (SQLException e) {
                errorMessage = e.getMessage();
                success = false;
            }

            long creationTimeMs = System.currentTimeMillis() - startTime;

            // Получаем размер индекса после создания
            Long indexSize = null;
            if (success) {
                try (PreparedStatement sizeStmt =
                        conn.prepareStatement(
                                "SELECT pg_relation_size(oid) FROM pg_class WHERE relname = ?")) {
                    sizeStmt.setString(1, indexName);
                    try (ResultSet rs = sizeStmt.executeQuery()) {
                        if (rs.next()) {
                            indexSize = rs.getLong(1);
                        }
                    }
                }
            }

            String performanceImpact =
                    success
                            ? String.format(
                                    "Индекс создан за %d мс, размер: %s",
                                    creationTimeMs, formatBytes(indexSize != null ? indexSize : 0))
                            : "Не удалось создать индекс";

            String recommendations =
                    success
                            ? "Индекс успешно создан. Рекомендуется выполнить ANALYZE для"
                                    + " обновления статистики."
                            : "Проверьте синтаксис SQL и наличие необходимых прав доступа.";

            return IndexCreationResult.builder()
                    .indexName(indexName)
                    .success(success)
                    .creationTime(creationTime)
                    .indexSize(indexSize)
                    .errorMessage(errorMessage)
                    .performanceImpact(performanceImpact)
                    .recommendations(recommendations)
                    .build();
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка создания индекса", ex);
        }
    }

    // Вспомогательные методы

    private void clearPlanCache(Connection connection) {
        try (Statement clearStmt = connection.createStatement()) {
            clearStmt.execute("DISCARD PLANS");
        } catch (SQLException e) {
            // Игнорируем ошибки очистки кэша
        }
    }

    private QueryExecutionPlan parseExecutionPlan(String query, String json) {
        if (json == null) {
            return QueryExecutionPlan.builder().originalQuery(query).usesIndexes(false).build();
        }

        BigDecimal planningTime =
                extractBigDecimal(json, "\"Planning Time\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
        BigDecimal executionTime =
                extractBigDecimal(json, "\"Execution Time\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");

        Pattern planPattern = Pattern.compile("\"Plan\"\\s*:\\s*\\{");
        Matcher planMatcher = planPattern.matcher(json);

        BigDecimal totalCost = null;
        List<PlanNode> nodes = new ArrayList<>();
        boolean usesIndexes = false;

        if (planMatcher.find()) {
            int start = planMatcher.end();
            String planJson = extractJsonObject(json, start - 1);

            totalCost = extractBigDecimal(planJson, "\"Total Cost\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
            if (totalCost == null) {
                totalCost =
                        extractBigDecimal(planJson, "\"total cost\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
            }

            PlanNode rootNode = parsePlanNode(planJson);
            if (rootNode != null) {
                nodes.add(rootNode);
                if (rootNode.getIndexName() != null && !rootNode.getIndexName().isEmpty()) {
                    usesIndexes = true;
                }
            }
        }

        // Проверяем использование индексов в тексте плана
        if (!usesIndexes) {
            usesIndexes =
                    json.toLowerCase().contains("index scan")
                            || json.toLowerCase().contains("bitmap");
        }

        String recommendations = generatePlanRecommendations(nodes, usesIndexes);

        return QueryExecutionPlan.builder()
                .originalQuery(query)
                .planText(json)
                .totalCost(totalCost)
                .executionTime(executionTime)
                .planningTime(planningTime)
                .rowsProcessed(
                        nodes.stream()
                                .map(PlanNode::getRows)
                                .filter(java.util.Objects::nonNull)
                                .reduce(0L, Long::sum))
                .planNodes(nodes)
                .usesIndexes(usesIndexes)
                .recommendations(recommendations)
                .build();
    }

    private PlanNode parsePlanNode(String nodeJson) {
        String nodeType = extractString(nodeJson, "\"Node Type\"\\s*:\\s*\"([^\"]+)\"");
        if (nodeType == null) {
            return null;
        }

        return PlanNode.builder()
                .nodeType(nodeType)
                .cost(extractBigDecimal(nodeJson, "\"Total Cost\"\\s*:\\s*(\\d+(?:\\.\\d+)?)"))
                .actualTime(
                        extractBigDecimal(
                                nodeJson, "\"Actual Total Time\"\\s*:\\s*(\\d+(?:\\.\\d+)?)"))
                .rows(extractLong(nodeJson, "\"Actual Rows\"\\s*:\\s*(\\d+)"))
                .relationName(extractString(nodeJson, "\"Relation Name\"\\s*:\\s*\"([^\"]+)\""))
                .indexName(extractString(nodeJson, "\"Index Name\"\\s*:\\s*\"([^\"]+)\""))
                .condition(extractString(nodeJson, "\"Filter\"\\s*:\\s*\"([^\"]+)\""))
                .build();
    }

    private String extractJsonObject(String json, int start) {
        int depth = 0;
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
            i++;
        }
        return json.substring(start);
    }

    private BigDecimal extractBigDecimal(String json, String pattern) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(json);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private String extractString(String json, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private Long extractLong(String json, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private Double getDoubleOrNull(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private List<String> analyzeQueryForIndexes(String query) {
        List<String> recommendations = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return recommendations;
        }

        String lowerQuery = query.toLowerCase();
        Pattern wherePattern = Pattern.compile("where\\s+([^\\s]+)\\s*=");
        Matcher whereMatcher = wherePattern.matcher(lowerQuery);
        while (whereMatcher.find()) {
            String column = whereMatcher.group(1);
            recommendations.add("Индекс на колонку: " + column);
        }

        Pattern joinPattern = Pattern.compile("join\\s+\\w+\\s+on\\s+([^\\s=]+)\\s*=");
        Matcher joinMatcher = joinPattern.matcher(lowerQuery);
        while (joinMatcher.find()) {
            String column = joinMatcher.group(1);
            recommendations.add("Индекс на колонку для JOIN: " + column);
        }

        return recommendations;
    }

    private String generateQueryOptimizationSuggestions(
            String query, List<String> recommendedIndexes) {
        if (recommendedIndexes == null || recommendedIndexes.isEmpty()) {
            return "Рекомендуется проанализировать план выполнения запроса с помощью EXPLAIN"
                    + " ANALYZE";
        }
        return "Рекомендуется создать следующие индексы: " + String.join(", ", recommendedIndexes);
    }

    private String generatePlanRecommendations(List<PlanNode> nodes, boolean usesIndexes) {
        if (usesIndexes) {
            return "Запрос использует индексы. Производительность оптимальна.";
        }
        if (nodes.stream().anyMatch(n -> n != null && "Seq Scan".equals(n.getNodeType()))) {
            return "Обнаружено последовательное сканирование. Рекомендуется создать индекс для"
                    + " условий WHERE и JOIN.";
        }
        return "Рекомендуется проанализировать запрос и создать необходимые индексы.";
    }

    private String generateCacheRecommendation(
            double overallHitRatio, double indexHitRatio, double tableHitRatio) {
        if (overallHitRatio >= 95.0) {
            return "Отличный hit ratio. Кэш работает эффективно.";
        } else if (overallHitRatio >= 90.0) {
            return "Хороший hit ratio. Рекомендуется мониторинг.";
        } else if (overallHitRatio >= 80.0) {
            return "Средний hit ratio. Рассмотрите увеличение shared_buffers.";
        } else {
            return "Низкий hit ratio. Требуется оптимизация: увеличьте shared_buffers или"
                    + " оптимизируйте запросы.";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
