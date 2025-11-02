/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.analytics.PlanNode;
import ru.mentee.power.model.analytics.QueryExecutionPlan;
import ru.mentee.power.model.mp163.IndexUsageStats;
import ru.mentee.power.model.mp163.OrderAnalytics;
import ru.mentee.power.model.mp163.PerformanceMetrics;
import ru.mentee.power.repository.interfaces.CompositeIndexRepository;

public class PostgresCompositeIndexRepository implements CompositeIndexRepository {
    private static final String[] SQL_CREATE_INDEX = {
        "CREATE INDEX idx_orders_user_status ON mentee_power.orders(user_id, status)",
        "CREATE INDEX idx_products_category_price ON mentee_power.products(category_id, price)",
        "CREATE UNIQUE INDEX idx_users_email_lower ON mentee_power.users(LOWER(email)) WHERE"
                + " is_active = true",
        "CREATE INDEX idx_products_expensive_active ON mentee_power.products(category_id, price"
                + " DESC) WHERE price > 1000",
        "CREATE INDEX idx_orders_status_created ON mentee_power.orders(status, created_at)"
    };

    private static final String EXPLAIN_ANALYZE_WRAPPER =
            """
      EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) %s
      """;

    private static final String[] SQL_DROP_INDEXES = {
        "DROP INDEX IF EXISTS mentee_power.idx_orders_user_status",
        "DROP INDEX IF EXISTS mentee_power.idx_products_category_price",
        "DROP INDEX IF EXISTS mentee_power.idx_users_email_lower",
        "DROP INDEX IF EXISTS mentee_power.idx_products_expensive_active",
        "DROP INDEX IF EXISTS mentee_power.idx_orders_status_created",
    };

    private ApplicationConfig config;

    public PostgresCompositeIndexRepository(ApplicationConfig config) {
        this.config = config;
    }

    private Connection getConnection() throws SQLException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (PreparedStatement stmt =
                conn.prepareStatement("SET search_path TO mentee_power, public")) {
            stmt.execute();
        }
        return conn;
    }

    private String determinePerformanceGrade(long executionTimeMs) {
        if (executionTimeMs < 100) return "EXCELLENT";
        if (executionTimeMs < 500) return "GOOD";
        if (executionTimeMs < 2000) return "POOR";
        return "CRITICAL";
    }

    /**
     * Очищает кэш планов выполнения.
     */
    private void clearPlanCache(Connection connection) {
        try (Statement clearStmt = connection.createStatement()) {
            clearStmt.execute("DISCARD PLANS");
        } catch (SQLException e) {
            // Игнорируем ошибки очистки кэша
        }
    }

    /**
     * Получает метрики из EXPLAIN ANALYZE для заданного запроса.
     *
     * @param connection соединение с БД
     * @param queryStr SQL запрос для анализа
     * @return метрики выполнения запроса или null, если не удалось получить
     */
    private ExecutionPlanMetrics getExecutionPlanMetrics(Connection connection, String queryStr) {
        ExecutionPlanMetrics metrics = new ExecutionPlanMetrics();

        try {
            clearPlanCache(connection);

            String explainQuery = String.format(EXPLAIN_ANALYZE_WRAPPER, queryStr);
            try (PreparedStatement explainStmt = connection.prepareStatement(explainQuery);
                    ResultSet explainRs = explainStmt.executeQuery()) {

                if (explainRs.next()) {
                    String queryPlan = explainRs.getString(1);
                    metrics.queryPlan = queryPlan;
                    QueryExecutionPlan plan = parseExecutionPlan(queryStr, queryPlan);

                    if (plan.getNodes() != null && !plan.getNodes().isEmpty()) {
                        extractMetricsFromPlan(plan, metrics);
                    }
                }
            }
        } catch (Exception e) {
            // Если не удалось получить метрики, возвращаем объект с null значениями
        }

        return metrics;
    }

    /**
     * Внутренний класс для хранения метрик плана выполнения.
     */
    private static class ExecutionPlanMetrics {
        String queryPlan;
        Long buffersHit;
        Long buffersRead;
        Long rowsScanned;
        String scanType;
        String indexesUsed;
        BigDecimal costEstimate;
    }

    /**
     * Извлекает метрики из плана выполнения.
     */
    private void extractMetricsFromPlan(QueryExecutionPlan plan, ExecutionPlanMetrics metrics) {
        if (plan.getNodes() == null || plan.getNodes().isEmpty()) {
            return;
        }

        // Суммируем buffers из всех узлов
        metrics.buffersHit =
                plan.getNodes().stream()
                        .map(n -> n.getBuffersHit())
                        .filter(h -> h != null)
                        .reduce(0L, Long::sum);
        metrics.buffersRead =
                plan.getNodes().stream()
                        .map(n -> n.getBuffersRead())
                        .filter(r -> r != null)
                        .reduce(0L, Long::sum);

        // Определяем тип сканирования
        metrics.scanType =
                plan.getNodes().stream()
                        .map(n -> n.getNodeType())
                        .filter(t -> t != null)
                        .findFirst()
                        .orElse(null);

        // Извлекаем использованные индексы
        List<String> indexes =
                plan.getNodes().stream()
                        .map(n -> n.getIndexName())
                        .filter(i -> i != null && !i.isEmpty())
                        .distinct()
                        .toList();
        metrics.indexesUsed = indexes.isEmpty() ? null : String.join(", ", indexes);

        // Получаем стоимость запроса
        metrics.costEstimate = plan.getTotalCost();

        // Определяем количество строк
        metrics.rowsScanned =
                plan.getNodes().stream()
                        .map(n -> n.getRows())
                        .filter(r -> r != null)
                        .reduce(0L, Long::sum);
    }

    /**
     * Формирует рекомендацию по оптимизации на основе использования индексов.
     */
    private String generateOptimizationRecommendation(
            String indexesUsed, String defaultRecommendation) {
        if (indexesUsed != null && !indexesUsed.isEmpty()) {
            return "Запрос использует индексы: " + indexesUsed + ". Производительность оптимальна.";
        } else {
            return defaultRecommendation != null
                    ? defaultRecommendation
                    : "Запрос не использует индексы.";
        }
    }

    /**
     * Строит объект PerformanceMetrics на основе данных и метрик выполнения.
     */
    private <T> PerformanceMetrics<T> buildPerformanceMetrics(
            T data,
            long executionTimeNanos,
            long executionTimeMs,
            LocalDateTime executedAt,
            ExecutionPlanMetrics planMetrics,
            Long rowsReturned,
            String optimizationRecommendation) {
        return PerformanceMetrics.<T>builder()
                .data(data)
                .executionTimeNanos(executionTimeNanos)
                .executionTimeMs(executionTimeMs)
                .queryPlan(planMetrics.queryPlan)
                .scanType(planMetrics.scanType)
                .buffersHit(
                        planMetrics.buffersHit != null && planMetrics.buffersHit > 0
                                ? planMetrics.buffersHit
                                : null)
                .buffersRead(
                        planMetrics.buffersRead != null && planMetrics.buffersRead > 0
                                ? planMetrics.buffersRead
                                : null)
                .rowsScanned(planMetrics.rowsScanned)
                .rowsReturned(rowsReturned)
                .performanceGrade(determinePerformanceGrade(executionTimeMs))
                .executedAt(executedAt)
                .indexesUsed(planMetrics.indexesUsed)
                .costEstimate(planMetrics.costEstimate)
                .optimizationRecommendation(optimizationRecommendation)
                .build();
    }

    private static final String ORDER_ANALYTICS_QUERY =
            """
      SELECT
          region,
          status,
          COUNT(*) as orders_count,
          SUM(total) as total_revenue,
          AVG(total) as avg_order_value,
          MIN(created_at) as first_order,
          MAX(created_at) as last_order
      FROM mentee_power.orders
      WHERE region = ANY(?)
        AND status = ANY(?)
        AND created_at >= ?
        AND created_at <= ?
      GROUP BY region, status
      ORDER BY region, status
      """;

    private PerformanceMetrics<List<OrderAnalytics>> executeOrderAnalytics(
            List<String> regions, List<String> statuses, LocalDate startDate, LocalDate endDate)
            throws DataAccessException {
        long startTimeNanos = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();
        List<OrderAnalytics> analytics = new ArrayList<>();

        try (Connection connection = getConnection()) {
            // Очищаем кэш планов выполнения перед измерением производительности
            try (Statement clearStmt = connection.createStatement()) {
                clearStmt.execute("DISCARD PLANS");
            } catch (SQLException e) {
                // Игнорируем ошибки очистки кэша
            }

            try (PreparedStatement statement = connection.prepareStatement(ORDER_ANALYTICS_QUERY)) {

                // Устанавливаем массивы для PostgreSQL
                Array regionsArray = connection.createArrayOf("VARCHAR", regions.toArray());
                Array statusesArray = connection.createArrayOf("VARCHAR", statuses.toArray());

                statement.setArray(1, regionsArray);
                statement.setArray(2, statusesArray);
                statement.setDate(3, Date.valueOf(startDate));
                statement.setTimestamp(4, Timestamp.valueOf(endDate.atTime(23, 59, 59)));

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        OrderAnalytics orderAnalytics =
                                OrderAnalytics.builder()
                                        .region(resultSet.getString("region"))
                                        .status(resultSet.getString("status"))
                                        .ordersCount(resultSet.getInt("orders_count"))
                                        .totalRevenue(resultSet.getBigDecimal("total_revenue"))
                                        .avgOrderValue(resultSet.getBigDecimal("avg_order_value"))
                                        .firstOrder(
                                                resultSet.getTimestamp("first_order") != null
                                                        ? resultSet
                                                                .getTimestamp("first_order")
                                                                .toLocalDateTime()
                                                        : null)
                                        .lastOrder(
                                                resultSet.getTimestamp("last_order") != null
                                                        ? resultSet
                                                                .getTimestamp("last_order")
                                                                .toLocalDateTime()
                                                        : null)
                                        .build();
                        analytics.add(orderAnalytics);
                    }
                }
            }

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            long executionTimeMsResult = System.currentTimeMillis() - startTimeMs;

            // Формируем строку запроса для EXPLAIN ANALYZE
            String regionsStr =
                    String.join(
                            ", ",
                            regions.stream().map(r -> "'" + r.replace("'", "''") + "'").toList());
            String statusesStr =
                    String.join(
                            ", ",
                            statuses.stream().map(s -> "'" + s.replace("'", "''") + "'").toList());

            String queryStr =
                    String.format(
                            "SELECT region, status, COUNT(*) as orders_count, SUM(total) as"
                                + " total_revenue, AVG(total) as avg_order_value, MIN(created_at)"
                                + " as first_order, MAX(created_at) as last_order FROM"
                                + " mentee_power.orders WHERE region = ANY(ARRAY[%s]) AND status ="
                                + " ANY(ARRAY[%s]) AND created_at >= '%s'::DATE AND created_at <="
                                + " '%s'::DATE GROUP BY region, status ORDER BY region, status",
                            regionsStr, statusesStr, startDate, endDate);

            // Получаем метрики из EXPLAIN ANALYZE
            ExecutionPlanMetrics planMetrics = getExecutionPlanMetrics(connection, queryStr);

            String optimizationRecommendation =
                    generateOptimizationRecommendation(
                            planMetrics.indexesUsed,
                            "Запрос не использует индексы. Рекомендуется создать составной индекс"
                                    + " на (region, status, created_at).");

            return buildPerformanceMetrics(
                    analytics,
                    executionTimeNanos,
                    executionTimeMsResult,
                    executedAt,
                    planMetrics,
                    (long) analytics.size(),
                    optimizationRecommendation);

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка выполнения аналитики заказов", e);
        }
    }

    @Override
    public PerformanceMetrics<List<OrderAnalytics>> getOrderAnalyticsWithoutIndex(
            List<String> regions, List<String> statuses, LocalDate startDate, LocalDate endDate)
            throws DataAccessException {
        dropCompositeIndexes();
        return executeOrderAnalytics(regions, statuses, startDate, endDate);
    }

    @Override
    public PerformanceMetrics<List<OrderAnalytics>> getOrderAnalyticsWithIndex(
            List<String> regions, List<String> statuses, LocalDate startDate, LocalDate endDate)
            throws DataAccessException {
        createCompositeIndexes();
        return executeOrderAnalytics(regions, statuses, startDate, endDate);
    }

    private static final String MEASURE_QUERY =
            """
      SELECT COUNT(*)
      FROM mentee_power.products
      WHERE category_id = ?
        AND price >= ?
        AND price <= ?
      """;

    private PerformanceMetrics<Long> executeMeasureQuery(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) throws DataAccessException {
        long startTimeNanos = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();
        Long result = null;

        try (Connection connection = getConnection()) {
            // Очищаем кэш планов выполнения перед измерением производительности
            try (Statement clearStmt = connection.createStatement()) {
                clearStmt.execute("DISCARD PLANS");
            } catch (SQLException e) {
                // Игнорируем ошибки очистки кэша
            }

            try (PreparedStatement statement = connection.prepareStatement(MEASURE_QUERY)) {

                statement.setLong(1, categoryId);
                statement.setBigDecimal(2, minPrice);
                statement.setBigDecimal(3, maxPrice);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        result = resultSet.getLong(1);
                    }
                }
            }

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            long executionTimeMsResult = System.currentTimeMillis() - startTimeMs;

            // Формируем строку запроса для EXPLAIN ANALYZE
            // Заменяем параметры на конкретные значения для EXPLAIN ANALYZE
            String queryStr =
                    MEASURE_QUERY
                            .replaceFirst("\\?", String.valueOf(categoryId))
                            .replaceFirst("\\?", minPrice.toString())
                            .replaceFirst("\\?", maxPrice.toString())
                            .trim();

            // Получаем метрики из EXPLAIN ANALYZE
            ExecutionPlanMetrics planMetrics = getExecutionPlanMetrics(connection, queryStr);

            String optimizationRecommendation =
                    generateOptimizationRecommendation(
                            planMetrics.indexesUsed,
                            "Запрос не использует индексы. Рекомендуется создать составной индекс"
                                    + " на (category_id, price).");

            return buildPerformanceMetrics(
                    result,
                    executionTimeNanos,
                    executionTimeMsResult,
                    executedAt,
                    planMetrics,
                    result != null ? 1L : 0L,
                    optimizationRecommendation);

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка выполнения запроса измерения", e);
        }
    }

    @Override
    public PerformanceMetrics<Long> measureQueryWithoutIndex(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) throws DataAccessException {
        dropCompositeIndexes();
        return executeMeasureQuery(categoryId, minPrice, maxPrice);
    }

    @Override
    public PerformanceMetrics<Long> measureQueryWithIndex(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) throws DataAccessException {
        createCompositeIndexes();
        return executeMeasureQuery(categoryId, minPrice, maxPrice);
    }

    @Override
    public PerformanceMetrics<String> createCompositeIndexes() throws DataAccessException {
        return executeIndexes(SQL_CREATE_INDEX);
    }

    @Override
    public PerformanceMetrics<String> dropCompositeIndexes() throws DataAccessException {
        return executeIndexes(SQL_DROP_INDEXES);
    }

    private PerformanceMetrics<String> executeIndexes(String[] sql) throws DataAccessException {
        long startTimeNanos = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();

        boolean isCreate = sql.length > 0 && sql[0].toUpperCase().contains("CREATE");
        boolean isDrop = sql.length > 0 && sql[0].toUpperCase().contains("DROP");
        String successMessage = isCreate ? "Индексы успешно созданы" : "Индексы успешно удалены";
        String errorMessage = isCreate ? "Ошибка создания индексов" : "Ошибка удаления индексов";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            for (String sqlCommand : sql) {
                try {
                    stmt.execute(sqlCommand);
                } catch (SQLException e) {
                    String errorMsg = e.getMessage().toLowerCase();
                    if (isCreate
                            && (errorMsg.contains("already exists")
                                    || errorMsg.contains("duplicate"))) {
                        continue;
                    }
                    if (isDrop
                            && (errorMsg.contains("does not exist")
                                    || errorMsg.contains("not found"))) {
                        continue;
                    }
                    throw e;
                }
            }
            if (isCreate || isDrop) {
                try {
                    stmt.execute("ANALYZE mentee_power.orders");
                    stmt.execute("ANALYZE mentee_power.products");
                    stmt.execute("ANALYZE mentee_power.users");
                } catch (SQLException e) {
                    // Игнорируем ошибки обновления статистики - это не критично
                }

                try {
                    stmt.execute("DISCARD PLANS");
                } catch (SQLException e) {
                    // Игнорируем ошибки очистки кэша - это не критично
                }
            }

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            long executionTimeMsResult = System.currentTimeMillis() - startTimeMs;
            return PerformanceMetrics.<String>builder()
                    .data(successMessage)
                    .executionTimeNanos(executionTimeNanos)
                    .executionTimeMs(executionTimeMsResult)
                    .executedAt(executedAt)
                    .performanceGrade(determinePerformanceGrade(executionTimeMsResult))
                    .optimizationRecommendation(
                            isCreate
                                    ? "Индексы созданы. Рекомендуется проверить их использование"
                                            + " через pg_stat_user_indexes."
                                    : "Индексы удалены. Производительность запросов может"
                                            + " снизиться.")
                    .build();

        } catch (SQLException ex) {
            throw new DataAccessException(errorMessage, ex);
        }
    }

    private QueryExecutionPlan parseExecutionPlan(String query, String json) {
        if (json == null) return QueryExecutionPlan.builder().query(query).build();

        BigDecimal planningTime =
                extractBigDecimal(json, "\"Planning Time\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
        BigDecimal executionTime =
                extractBigDecimal(json, "\"Execution Time\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");

        // Извлекаем корневой план
        Pattern planPattern = Pattern.compile("\"Plan\"\\s*:\\s*\\{");
        Matcher planMatcher = planPattern.matcher(json);

        BigDecimal totalCost = null;
        List<PlanNode> nodes = new ArrayList<>();

        if (planMatcher.find()) {
            int start = planMatcher.end();
            String planJson = extractJsonObject(json, start - 1);

            totalCost = extractBigDecimal(planJson, "\"Total Cost\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
            if (totalCost == null) {
                totalCost =
                        extractBigDecimal(planJson, "\"total cost\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
            }

            PlanNode rootNode = parsePlanNode(planJson);
            nodes.add(rootNode);
        }

        return QueryExecutionPlan.builder()
                .query(query)
                .planText(json)
                .totalCost(totalCost)
                .planningTime(planningTime)
                .executionTime(executionTime)
                .nodes(nodes)
                .build();
    }

    private PlanNode parsePlanNode(String nodeJson) {
        return PlanNode.builder()
                .nodeType(extractString(nodeJson, "\"Node Type\"\\s*:\\s*\"([^\"]+)\""))
                .cost(extractBigDecimal(nodeJson, "\"Total Cost\"\\s*:\\s*(\\d+(?:\\.\\d+)?)"))
                .actualTime(
                        extractBigDecimal(
                                nodeJson, "\"Actual Total Time\"\\s*:\\s*(\\d+(?:\\.\\d+)?)"))
                .rows(extractLong(nodeJson, "\"Actual Rows\"\\s*:\\s*(\\d+)"))
                .operation(extractString(nodeJson, "\"Operation\"\\s*:\\s*\"([^\"]+)\""))
                .relation(extractString(nodeJson, "\"Relation Name\"\\s*:\\s*\"([^\"]+)\""))
                .indexName(extractString(nodeJson, "\"Index Name\"\\s*:\\s*\"([^\"]+)\""))
                .filter(extractString(nodeJson, "\"Filter\"\\s*:\\s*\"([^\"]+)\""))
                .buffersHit(extractLong(nodeJson, "\"shared hit blocks\"\\s*:\\s*(\\d+)"))
                .buffersRead(extractLong(nodeJson, "\"shared read blocks\"\\s*:\\s*(\\d+)"))
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

    private static final String INDEX_USAGE_STATS_QUERY =
            """
      SELECT
          pi.schemaname,
          pi.tablename,
          pi.indexname,
          COALESCE(psui.idx_scan, 0) as total_scans,
          COALESCE(psui.idx_tup_read, 0) as tuples_read,
          COALESCE(psui.idx_tup_fetch, 0) as tuples_returned,
          pg_relation_size((pi.schemaname||'.'||pi.indexname)::regclass) as size_bytes,
          pi.indexdef as definition,
          CASE
              WHEN pi.indexdef LIKE '%UNIQUE%' THEN 1.0
              ELSE NULL
          END as selectivity_estimate
      FROM pg_indexes pi
      LEFT JOIN pg_stat_user_indexes psui
          ON pi.schemaname = psui.schemaname
          AND pi.indexname = psui.indexrelname::text
      WHERE (pi.schemaname = 'mentee_power' OR pi.schemaname = 'public' OR pi.schemaname = current_schema())
        AND pi.indexname IN (
            'idx_orders_user_status',
            'idx_products_category_price',
            'idx_users_email_lower',
            'idx_products_expensive_active',
            'idx_orders_status_created'
        )
      ORDER BY COALESCE(psui.idx_scan, 0) DESC, pi.indexname
      """;

    @Override
    public List<IndexUsageStats> analyzeCompositeIndexUsage() throws DataAccessException {
        List<IndexUsageStats> statsList = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(INDEX_USAGE_STATS_QUERY);
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                String indexName = resultSet.getString("indexname");
                String tableName = resultSet.getString("tablename");
                Long totalScans = resultSet.getLong("total_scans");
                Long tuplesRead = resultSet.getLong("tuples_read");
                Long tuplesReturned = resultSet.getLong("tuples_returned");
                Long sizeBytes = resultSet.getLong("size_bytes");
                String definition = resultSet.getString("definition");
                Double selectivityEstimate =
                        resultSet.wasNull() ? null : resultSet.getDouble("selectivity_estimate");

                // Если selectivity_estimate NULL, вычисляем на основе использования
                Double selectivity = selectivityEstimate;
                if (selectivity == null && totalScans > 0 && tuplesRead > 0 && tuplesReturned > 0) {
                    // Приблизительная оценка эффективности: отношение возвращенных кортежей к
                    // прочитанным
                    // Для более точной оценки нужна информация о количестве строк в таблице
                    selectivity = (double) tuplesReturned / tuplesRead;
                    // Ограничиваем значение от 0.0 до 1.0
                    selectivity = Math.max(0.0, Math.min(1.0, selectivity));
                }

                // Формируем рекомендацию на основе статистики
                String recommendedUsage =
                        generateIndexRecommendation(
                                indexName, totalScans, tuplesRead, tuplesReturned);

                IndexUsageStats stats =
                        IndexUsageStats.builder()
                                .indexName(indexName)
                                .tableName(tableName)
                                .totalScans(totalScans)
                                .tuplesRead(tuplesRead > 0 ? tuplesRead : null)
                                .tuplesReturned(tuplesReturned > 0 ? tuplesReturned : null)
                                .selectivity(selectivity)
                                .sizeBytes(sizeBytes > 0 ? sizeBytes : null)
                                .definition(definition)
                                .recommendedUsage(recommendedUsage)
                                .build();

                statsList.add(stats);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка получения статистики использования индексов", e);
        }

        return statsList;
    }

    /**
     * Генерирует рекомендацию по использованию индекса на основе статистики.
     */
    private String generateIndexRecommendation(
            String indexName, Long totalScans, Long tuplesRead, Long tuplesReturned) {
        if (totalScans == 0) {
            return "Индекс не используется. Рекомендуется проверить необходимость индекса или"
                    + " удалить его.";
        }

        if (totalScans < 10) {
            return "Индекс редко используется. Рекомендуется проверить, используется ли он в"
                    + " критических запросах.";
        }

        if (tuplesRead > 0 && tuplesReturned > 0) {
            double efficiency = (double) tuplesReturned / tuplesRead;
            if (efficiency > 0.8) {
                return "Высокая эффективность использования индекса. Индекс работает оптимально.";
            } else if (efficiency > 0.5) {
                return "Умеренная эффективность использования индекса.";
            } else {
                return "Низкая эффективность использования индекса. Рекомендуется пересмотреть"
                        + " структуру индекса.";
            }
        }

        return "Индекс используется регулярно. Статистика эффективности недоступна.";
    }
}
