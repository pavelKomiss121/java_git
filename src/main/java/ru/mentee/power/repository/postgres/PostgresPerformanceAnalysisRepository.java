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
import ru.mentee.power.model.analytics.PerformanceMetrics;
import ru.mentee.power.model.analytics.PlanNode;
import ru.mentee.power.model.analytics.QueryExecutionPlan;
import ru.mentee.power.model.analytics.UserOrderStats;
import ru.mentee.power.repository.interfaces.PerformanceAnalysisRepository;

public class PostgresPerformanceAnalysisRepository implements PerformanceAnalysisRepository {

    private static final String HEAVY_USER_ORDERS_QUERY =
            """
    SELECT
        u.id as user_id,
        u.name as user_name,
        u.email,
        COUNT(o.id) as orders_count,
        SUM(o.total) as total_spent,
        AVG(o.total) as avg_order_value
    FROM users u
    JOIN orders o ON u.id = o.user_id
    WHERE u.city = ?
      AND o.created_at >= ?
      AND o.status = 'DELIVERED'
    GROUP BY u.id, u.name, u.email
    HAVING COUNT(o.id) > ?
    ORDER BY total_spent DESC
    LIMIT 20
    """;

    private static final String EXPLAIN_ANALYZE_WRAPPER =
            """
    EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) %s
    """;

    private static final String[] CREATE_PERFORMANCE_INDEXES = {
        "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_city ON users(city)",
        "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_user_date_status ON orders(user_id,"
                + " created_at, status)",
        "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_status_date ON orders(status,"
                + " created_at)"
    };

    private static final String[] DROP_PERFORMANCE_INDEXES = {
        "DROP INDEX IF EXISTS idx_users_city",
        "DROP INDEX IF EXISTS idx_orders_user_date_status",
        "DROP INDEX IF EXISTS idx_orders_status_date"
    };

    private ApplicationConfig config;

    PostgresPerformanceAnalysisRepository(ApplicationConfig config) {
        this.config = config;
    }

    protected Connection getConnection() throws SQLException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        // Устанавливаем search_path на нужную схему
        try (PreparedStatement stmt =
                conn.prepareStatement("SET search_path TO mentee_power, public")) {
            stmt.execute();
        }
        return conn;
    }

    @Override
    public PerformanceMetrics<List<UserOrderStats>> getSlowUserOrderStats(
            String city, LocalDate startDate, Integer minOrders) throws DataAccessException {
        return getUserOrderStats(
                city,
                startDate,
                minOrders,
                "SLOW_QUERY_NO_INDEXES",
                "Ошибка медленного запроса без ИНДЕКСОВ");
    }

    private String determinePerformanceGrade(long executionTimeMs) {
        if (executionTimeMs < 100) return "EXCELLENT";
        if (executionTimeMs < 500) return "GOOD";
        if (executionTimeMs < 2000) return "POOR";
        return "CRITICAL";
    }

    @Override
    public PerformanceMetrics<List<UserOrderStats>> getFastUserOrderStats(
            String city, LocalDate startDate, Integer minOrders) throws DataAccessException {
        return getUserOrderStats(
                city,
                startDate,
                minOrders,
                "FAST_QUERY_WITH_INDEXES",
                "Ошибка быстрого запроса с ИНДЕКСАМИ");
    }

    private PerformanceMetrics<List<UserOrderStats>> getUserOrderStats(
            String city,
            LocalDate startDate,
            Integer minOrders,
            String queryType,
            String errorMessage)
            throws DataAccessException {
        List<UserOrderStats> userOrderStats = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();

        try (Connection connection = getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(HEAVY_USER_ORDERS_QUERY); ) {
            statement.setString(1, city);
            statement.setDate(2, Date.valueOf(startDate));
            statement.setInt(3, minOrders);

            try (ResultSet resultSet = statement.executeQuery(); ) {
                while (resultSet.next()) {
                    UserOrderStats user =
                            UserOrderStats.builder()
                                    .userId(resultSet.getLong("user_id"))
                                    .userName(resultSet.getString("user_name"))
                                    .ordersCount(resultSet.getInt("orders_count"))
                                    .email(resultSet.getString("email"))
                                    .avgOrderValue(resultSet.getBigDecimal("avg_order_value"))
                                    .totalSpent(resultSet.getBigDecimal("total_spent"))
                                    .build();
                    userOrderStats.add(user);
                }
            }

            long executionTimeMs = System.currentTimeMillis() - startTime;

            // Получаем метрики из EXPLAIN ANALYZE
            Long planningMs = null;
            Long buffersHit = null;
            Long buffersRead = null;

            try {
                String queryStr =
                        String.format(
                                "SELECT u.id as user_id, u.name as user_name, u.email, COUNT(o.id)"
                                    + " as orders_count, SUM(o.total) as total_spent, AVG(o.total)"
                                    + " as avg_order_value FROM users u JOIN orders o ON u.id ="
                                    + " o.user_id WHERE u.city = '%s' AND o.created_at >="
                                    + " '%s'::DATE AND o.status = 'DELIVERED' GROUP BY u.id,"
                                    + " u.name, u.email HAVING COUNT(o.id) > %s ORDER BY"
                                    + " total_spent DESC LIMIT 20",
                                city.replace("'", "''"), startDate, minOrders);

                QueryExecutionPlan plan = getExecutionPlan(queryStr);
                planningMs =
                        plan.getPlanningTime() != null
                                ? Math.round(plan.getPlanningTime().doubleValue() * 1000)
                                : null;

                if (plan.getNodes() != null) {
                    buffersHit =
                            plan.getNodes().stream()
                                    .map(n -> n.getBuffersHit())
                                    .filter(h -> h != null)
                                    .reduce(0L, Long::sum);
                    buffersRead =
                            plan.getNodes().stream()
                                    .map(n -> n.getBuffersRead())
                                    .filter(r -> r != null)
                                    .reduce(0L, Long::sum);
                }
            } catch (DataAccessException e) {
                // Если не удалось получить метрики, оставляем null
            }

            return PerformanceMetrics.<List<UserOrderStats>>builder()
                    .data(userOrderStats)
                    .executionTimeMs(executionTimeMs)
                    .planningTimeMs(planningMs)
                    .buffersHit(buffersHit != null && buffersHit > 0 ? buffersHit : null)
                    .buffersRead(buffersRead != null && buffersRead > 0 ? buffersRead : null)
                    .queryType(queryType)
                    .executedAt(executedAt)
                    .performanceGrade(determinePerformanceGrade(executionTimeMs))
                    .build();
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    @Override
    public QueryExecutionPlan getExecutionPlan(String query) throws DataAccessException {
        try (Connection conn = getConnection()) {
            String explainQuery = String.format(EXPLAIN_ANALYZE_WRAPPER, query);

            try (PreparedStatement stmt = conn.prepareStatement(explainQuery);
                    ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString(1);
                    return parseExecutionPlan(query, json);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка получения плана выполнения", e);
        }
        return QueryExecutionPlan.builder().query(query).build();
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

    @Override
    public PerformanceMetrics<String> createOptimizationIndexes() throws DataAccessException {
        long startTime = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            for (String createIndex : CREATE_PERFORMANCE_INDEXES) {
                stmt.execute(createIndex);
            }

            long executionTimeMs = System.currentTimeMillis() - startTime;

            return PerformanceMetrics.<String>builder()
                    .data("Индексы успешно созданы")
                    .executionTimeMs(executionTimeMs)
                    .queryType("CREATE_INDEXES")
                    .executedAt(executedAt)
                    .performanceGrade(determinePerformanceGrade(executionTimeMs))
                    .build();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка создания индексов", e);
        }
    }

    @Override
    public PerformanceMetrics<String> dropOptimizationIndexes() throws DataAccessException {
        long startTime = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            for (String dropIndex : DROP_PERFORMANCE_INDEXES) {
                stmt.execute(dropIndex);
            }

            long executionTimeMs = System.currentTimeMillis() - startTime;

            return PerformanceMetrics.<String>builder()
                    .data("Индексы успешно удалены")
                    .executionTimeMs(executionTimeMs)
                    .queryType("DROP_INDEXES")
                    .executedAt(executedAt)
                    .performanceGrade(determinePerformanceGrade(executionTimeMs))
                    .build();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка удаления индексов", e);
        }
    }
}
