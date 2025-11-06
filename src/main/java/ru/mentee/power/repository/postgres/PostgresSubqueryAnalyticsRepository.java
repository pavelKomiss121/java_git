/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp169.*;
import ru.mentee.power.repository.interfaces.SubqueryAnalyticsRepository;

public class PostgresSubqueryAnalyticsRepository implements SubqueryAnalyticsRepository {

    private final String SQL_FIND_VIP_CUSTOMERS =
            """
    SELECT
    u.id,
    u.first_name,
    u.last_name,
    u.email,
    u.customer_tier,
    u.registration_date,
    (SELECT SUM(total_amount) FROM mentee_power.orders WHERE user_id = u.id) AS total_amount,
    (SELECT AVG(total_amount) FROM mentee_power.orders WHERE user_id = u.id) as avg_customer_spending,
    (SELECT AVG(total_amount) FROM mentee_power.orders) as system_avg_order_value,
    (SELECT COUNT(*) FROM mentee_power.orders WHERE user_id = u.id) AS order_count,
    (SELECT MAX(created_at) FROM mentee_power.orders WHERE user_id = u.id) AS last_purchase_date
FROM mentee_power.users as u
WHERE (
    SELECT SUM(total_amount) FROM mentee_power.orders WHERE user_id = u.id
) > (
    SELECT AVG(customer_totals.total_spent) * ?
     FROM (
         SELECT SUM(total_amount) as total_spent
         FROM mentee_power.orders
         GROUP BY user_id
     ) as customer_totals
)
ORDER BY total_amount DESC
LIMIT ?;
""";

    private final String SQL_FIND_UNSOLD_PRODUCTS_TEMPLATE =
            """
    SELECT
        p.id,
        p.name,
        p.sku,
        p.price,
        p.stock_quantity,
        p.created_at,
        c.name as category_name,
        (SELECT MAX(o.created_at)
         FROM mentee_power.order_items oi
         JOIN mentee_power.orders o ON oi.order_id = o.id
         WHERE oi.product_id = p.id
         AND o.status IN ('DELIVERED', 'SHIPPED', 'COMPLETED')
        ) as last_sale_date
    FROM mentee_power.products p
    LEFT JOIN mentee_power.categories c ON p.category_id = c.id
    WHERE NOT EXISTS (
        SELECT 1
        FROM mentee_power.order_items oi
        JOIN mentee_power.orders o ON oi.order_id = o.id
        WHERE oi.product_id = p.id
        AND o.created_at >= NOW() - INTERVAL '%d days'
        AND o.status IN ('DELIVERED', 'SHIPPED', 'COMPLETED')
    )
    AND (CASE WHEN ? THEN TRUE ELSE p.created_at < NOW() - INTERVAL '%d days' END)
    ORDER BY p.created_at DESC;
    """;

    private final String SQL_FIND_ANOMALOUS_ORDERS_TEMPLATE =
            """
    SELECT
        o.id as order_id,
        o.user_id,
        u.email as user_email,
        CONCAT(u.first_name, ' ', u.last_name) as user_name,
        o.total_amount as order_amount,
        (SELECT AVG(total_amount)
         FROM mentee_power.orders
         WHERE user_id = o.user_id
         AND created_at >= NOW() - INTERVAL '%d months'
        ) as user_avg_order_amount,
        (SELECT COUNT(*)
         FROM mentee_power.order_items
         WHERE order_id = o.id
        ) as item_count,
        (SELECT STRING_AGG(DISTINCT c.name, ', ')
         FROM mentee_power.order_items oi
         JOIN mentee_power.products p ON oi.product_id = p.id
         LEFT JOIN mentee_power.categories c ON p.category_id = c.id
         WHERE oi.order_id = o.id
        ) as product_categories
    FROM mentee_power.orders o
    JOIN mentee_power.users u ON o.user_id = u.id
    WHERE o.created_at >= NOW() - INTERVAL '%d months'
    AND o.total_amount > (
        SELECT AVG(total_amount) * (? / 100.0)
        FROM mentee_power.orders
        WHERE user_id = o.user_id
        AND created_at >= NOW() - INTERVAL '%d months'
    )
    ORDER BY o.total_amount DESC;
    """;

    private final String SQL_GET_USER_ACTIVITY_UNION =
            """
    SELECT
        'ORDER' as activity_type,
        o.created_at as activity_date,
        o.total_amount as activity_value,
        CONCAT('Заказ #', o.id, ' на сумму ', o.total_amount) as description,
        o.status as status
    FROM mentee_power.orders o
    WHERE o.user_id = ?
    AND o.created_at >= ?

    UNION ALL

    SELECT
        'REVIEW' as activity_type,
        NOW() as activity_date,
        NULL as activity_value,
        'Отзыв на товар' as description,
        'ACTIVE' as status
    FROM mentee_power.users u
    WHERE u.id = ?
    AND u.id IN (SELECT user_id FROM mentee_power.orders WHERE user_id = ?)

    UNION ALL

    SELECT
        'PROMOTION' as activity_type,
        NOW() as activity_date,
        NULL as activity_value,
        'Использован промокод' as description,
        'ACTIVE' as status
    FROM mentee_power.users u
    WHERE u.id = ?
    AND u.id IN (SELECT user_id FROM mentee_power.orders WHERE user_id = ?)

    ORDER BY activity_date DESC
    LIMIT ?;
    """;

    private final String SQL_GET_SALES_REPORT_UNION =
            """
    SELECT
        c.name as dimension_name,
        'CATEGORY' as dimension_type,
        SUM(o.total_amount) as total_sales,
        COUNT(DISTINCT o.id) as order_count,
        COUNT(oi.id) as item_count,
        AVG(o.total_amount) as avg_order_value
    FROM mentee_power.orders o
    JOIN mentee_power.order_items oi ON o.id = oi.order_id
    JOIN mentee_power.products p ON oi.product_id = p.id
    LEFT JOIN mentee_power.categories c ON p.category_id = c.id
    WHERE o.created_at BETWEEN ? AND ?
    GROUP BY c.name
    HAVING SUM(o.total_amount) >= ?

    UNION ALL

    SELECT
        o.region as dimension_name,
        'ORDER_SOURCE' as dimension_type,
        SUM(o.total_amount) as total_sales,
        COUNT(DISTINCT o.id) as order_count,
        COUNT(oi.id) as item_count,
        AVG(o.total_amount) as avg_order_value
    FROM mentee_power.orders o
    JOIN mentee_power.order_items oi ON o.id = oi.order_id
    WHERE o.created_at BETWEEN ? AND ?
    GROUP BY o.region
    HAVING SUM(o.total_amount) >= ?

    ORDER BY total_sales DESC;
    """;

    private final String SQL_COMPARE_SUBQUERY_PERFORMANCE_TEMPLATE =
            """
    SELECT
        COUNT(DISTINCT u.id),
        COALESCE((SELECT SUM(total_amount)
                  FROM mentee_power.orders o
                  WHERE o.user_id IN (
                      SELECT id FROM mentee_power.users
                      WHERE customer_tier = ?
                  )
                  AND o.created_at >= NOW() - INTERVAL '%d months'), 0)
    FROM mentee_power.users u
    WHERE u.customer_tier = ?
    AND EXISTS (
        SELECT 1 FROM mentee_power.orders o
        WHERE o.user_id = u.id
        AND o.created_at >= NOW() - INTERVAL '%d months'
    )
    """;

    private final String SQL_COMPARE_JOIN_PERFORMANCE_TEMPLATE =
            """
    SELECT COUNT(DISTINCT u.id), SUM(o.total_amount)
    FROM mentee_power.users u
    INNER JOIN mentee_power.orders o ON u.id = o.user_id
    WHERE u.customer_tier = ?
    AND o.created_at >= NOW() - INTERVAL '%d months'
    """;

    private final String SQL_ANALYZE_CATEGORY_GROWTH_TEMPLATE =
            """
    WITH current_period AS (
        SELECT
            c.id as category_id,
            c.name as category_name,
            SUM(o.total_amount) as total_sales,
            COUNT(DISTINCT o.id) as order_count
        FROM mentee_power.categories c
        JOIN mentee_power.products p ON c.id = p.category_id
        JOIN mentee_power.order_items oi ON p.id = oi.product_id
        JOIN mentee_power.orders o ON oi.order_id = o.id
        WHERE o.created_at >= NOW() - INTERVAL '%d months'
        GROUP BY c.id, c.name
    ),
    previous_period AS (
        SELECT
            c.id as category_id,
            SUM(o.total_amount) as total_sales,
            COUNT(DISTINCT o.id) as order_count
        FROM mentee_power.categories c
        JOIN mentee_power.products p ON c.id = p.category_id
        JOIN mentee_power.order_items oi ON p.id = oi.product_id
        JOIN mentee_power.orders o ON oi.order_id = o.id
        WHERE o.created_at >= NOW() - INTERVAL '%d months' * 2
        AND o.created_at < NOW() - INTERVAL '%d months'
        GROUP BY c.id
    )
    SELECT
        cp.category_id,
        cp.category_name,
        cp.total_sales,
        COALESCE(pp.total_sales, 0) as previous_sales,
        cp.order_count,
        COALESCE(pp.order_count, 0) as previous_order_count
    FROM current_period cp
    LEFT JOIN previous_period pp ON cp.category_id = pp.category_id
    ORDER BY cp.total_sales DESC
    LIMIT ?;
    """;

    private final String SQL_FIND_UPSELLING_OPPORTUNITIES =
            """
    SELECT
        u.id,
        u.email,
        u.first_name,
        u.last_name,
        u.customer_tier as current_tier,
        (SELECT SUM(total_amount) FROM mentee_power.orders WHERE user_id = u.id) as current_total_spent,
        (SELECT COUNT(*) FROM mentee_power.orders WHERE user_id = u.id) as current_order_count,
        (SELECT AVG(total_amount) FROM mentee_power.orders WHERE user_id = u.id) as avg_order_value,
        (SELECT MAX(created_at) FROM mentee_power.orders WHERE user_id = u.id) as last_purchase_date,
        (SELECT AVG(total_spent)
         FROM (
             SELECT SUM(total_amount) as total_spent
             FROM mentee_power.orders
             JOIN mentee_power.users usr ON orders.user_id = usr.id
             WHERE usr.customer_tier = ?
             GROUP BY user_id
         ) tier_stats
        ) as target_tier_minimum
    FROM mentee_power.users u
    WHERE u.customer_tier != ?
    AND (SELECT SUM(total_amount) FROM mentee_power.orders WHERE user_id = u.id) < (
        SELECT AVG(total_spent)
        FROM (
            SELECT SUM(total_amount) as total_spent
            FROM mentee_power.orders
            JOIN mentee_power.users usr ON orders.user_id = usr.id
            WHERE usr.customer_tier = ?
            GROUP BY user_id
        ) tier_stats
    )
    AND (SELECT SUM(total_amount) FROM mentee_power.orders WHERE user_id = u.id) > (
        SELECT AVG(total_spent) * 0.7
        FROM (
            SELECT SUM(total_amount) as total_spent
            FROM mentee_power.orders
            JOIN mentee_power.users usr ON orders.user_id = usr.id
            WHERE usr.customer_tier = ?
            GROUP BY user_id
        ) tier_stats
    )
    ORDER BY current_total_spent DESC
    LIMIT ?;
    """;

    private ApplicationConfig config;

    public PostgresSubqueryAnalyticsRepository(ApplicationConfig config) {
        this.config = config;
    }

    protected Connection getConnection() throws SQLException, DataAccessException {
        Connection connection =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (PreparedStatement statement =
                connection.prepareStatement("SET search_path TO mentee_power, public")) {
            statement.execute();
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка подключения", ex);
        }
        return connection;
    }

    @Override
    public List<VipCustomerAnalytics> findVipCustomersWithSubqueries(
            Double minSpendingMultiplier, Integer limit) throws DataAccessException, SQLException {
        List<VipCustomerAnalytics> vipCustomers = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(SQL_FIND_VIP_CUSTOMERS)) {
            statement.setDouble(1, minSpendingMultiplier);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Long userId = rs.getLong("id");
                    String firstName = rs.getString("first_name");
                    String lastName = rs.getString("last_name");
                    String email = rs.getString("email");
                    String customerLevel = rs.getString("customer_tier");
                    BigDecimal totalSpent = rs.getBigDecimal("total_amount");
                    if (totalSpent == null) {
                        totalSpent = BigDecimal.ZERO;
                    }
                    BigDecimal averageOrderValue = rs.getBigDecimal("avg_customer_spending");
                    BigDecimal systemAverageOrderValue = rs.getBigDecimal("system_avg_order_value");
                    BigDecimal averageMultiplier = BigDecimal.valueOf(minSpendingMultiplier);
                    Integer orderCount = rs.getInt("order_count");
                    if (rs.wasNull()) {
                        orderCount = 0;
                    }

                    Timestamp registrationTimestamp = rs.getTimestamp("registration_date");
                    LocalDateTime registrationDate =
                            registrationTimestamp != null
                                    ? registrationTimestamp.toLocalDateTime()
                                    : null;

                    Timestamp lastPurchaseTimestamp = rs.getTimestamp("last_purchase_date");
                    LocalDateTime lastPurchaseDate =
                            lastPurchaseTimestamp != null
                                    ? lastPurchaseTimestamp.toLocalDateTime()
                                    : null;

                    String vipStatusReason =
                            String.format(
                                    "Траты (%.2f) превышают средние траты клиентов в %.2f раз",
                                    totalSpent.doubleValue(), minSpendingMultiplier);

                    VipCustomerAnalytics vipCustomer =
                            VipCustomerAnalytics.builder()
                                    .userId(userId)
                                    .email(email)
                                    .firstName(firstName)
                                    .lastName(lastName)
                                    .customerLevel(customerLevel)
                                    .totalSpent(totalSpent)
                                    .averageOrderValue(averageOrderValue)
                                    .systemAverageOrderValue(systemAverageOrderValue)
                                    .averageMultiplier(averageMultiplier)
                                    .orderCount(orderCount)
                                    .registrationDate(registrationDate)
                                    .lastPurchaseDate(lastPurchaseDate)
                                    .vipStatusReason(vipStatusReason)
                                    .build();

                    vipCustomers.add(vipCustomer);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка поиска VIP клиентов", e);
        }
        return vipCustomers;
    }

    @Override
    public List<UnsoldProductAnalytics> findUnsoldProductsWithExists(
            Integer daysSinceLastSale, Boolean includeNewProducts)
            throws DataAccessException, SQLException {
        List<UnsoldProductAnalytics> unsoldProducts = new ArrayList<>();

        // Формируем SQL с подстановкой параметра для INTERVAL
        String sql =
                String.format(
                        SQL_FIND_UNSOLD_PRODUCTS_TEMPLATE, daysSinceLastSale, daysSinceLastSale);

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setBoolean(1, includeNewProducts);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Long productId = rs.getLong("id");
                    String productName = rs.getString("name");
                    String sku = rs.getString("sku");
                    String category = rs.getString("category_name");
                    BigDecimal price = rs.getBigDecimal("price");
                    if (price == null) {
                        price = BigDecimal.ZERO;
                    }

                    String brand = null;
                    BigDecimal costPrice = null;

                    Integer stockQuantity = rs.getInt("stock_quantity");
                    if (rs.wasNull()) {
                        stockQuantity = 0;
                    }

                    Timestamp lastSaleTimestamp = rs.getTimestamp("last_sale_date");
                    LocalDateTime lastSaleDate =
                            lastSaleTimestamp != null ? lastSaleTimestamp.toLocalDateTime() : null;

                    Integer daysWithoutSales;
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    LocalDateTime referenceDate =
                            lastSaleDate != null
                                    ? lastSaleDate
                                    : (createdAt != null
                                            ? createdAt.toLocalDateTime()
                                            : LocalDateTime.now());
                    long days =
                            java.time.Duration.between(referenceDate, LocalDateTime.now()).toDays();
                    daysWithoutSales = (int) days;

                    BigDecimal inventoryValue = price.multiply(BigDecimal.valueOf(stockQuantity));

                    String recommendedAction;
                    if (daysWithoutSales > 180) {
                        recommendedAction = "Утилизация или распродажа";
                    } else if (daysWithoutSales > 90) {
                        recommendedAction = "Скидка 50%";
                    } else if (daysWithoutSales > 60) {
                        recommendedAction = "Скидка 30%";
                    } else {
                        recommendedAction = "Мониторинг";
                    }

                    // Причина неликвидности
                    String unsoldReason;
                    if (lastSaleDate == null) {
                        unsoldReason = "Товар никогда не продавался";
                    } else if (daysWithoutSales > 180) {
                        unsoldReason =
                                String.format(
                                        "Последняя продажа была %d дней назад", daysWithoutSales);
                    } else {
                        unsoldReason =
                                String.format("Нет продаж за последние %d дней", daysSinceLastSale);
                    }

                    UnsoldProductAnalytics product =
                            UnsoldProductAnalytics.builder()
                                    .productId(productId)
                                    .productName(productName)
                                    .sku(sku)
                                    .brand(brand)
                                    .category(category)
                                    .price(price)
                                    .costPrice(costPrice)
                                    .stockQuantity(stockQuantity)
                                    .lastSaleDate(lastSaleDate)
                                    .daysWithoutSales(daysWithoutSales)
                                    .inventoryValue(inventoryValue)
                                    .recommendedAction(recommendedAction)
                                    .unsoldReason(unsoldReason)
                                    .build();

                    unsoldProducts.add(product);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка поиска неликвидных товаров", e);
        }
        return unsoldProducts;
    }

    @Override
    public List<AnomalousOrderAnalytics> findAnomalousOrdersWithCorrelatedSubqueries(
            Double anomalyThresholdPercent, Integer analysisMonths) throws DataAccessException {
        List<AnomalousOrderAnalytics> anomalousOrders = new ArrayList<>();
        String sql =
                String.format(
                        SQL_FIND_ANOMALOUS_ORDERS_TEMPLATE,
                        analysisMonths,
                        analysisMonths,
                        analysisMonths);

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setDouble(1, anomalyThresholdPercent);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Long orderId = rs.getLong("order_id");
                    Long userId = rs.getLong("user_id");
                    String userEmail = rs.getString("user_email");
                    String userName = rs.getString("user_name");
                    BigDecimal orderAmount = rs.getBigDecimal("order_amount");
                    if (orderAmount == null) {
                        orderAmount = BigDecimal.ZERO;
                    }

                    BigDecimal userAverageOrderAmount = rs.getBigDecimal("user_avg_order_amount");

                    BigDecimal anomalyCoefficient = BigDecimal.ZERO;
                    if (userAverageOrderAmount != null
                            && userAverageOrderAmount.compareTo(BigDecimal.ZERO) > 0) {
                        anomalyCoefficient =
                                orderAmount.divide(
                                        userAverageOrderAmount, 2, java.math.RoundingMode.HALF_UP);
                    }

                    Integer itemCount = rs.getInt("item_count");
                    if (rs.wasNull()) {
                        itemCount = 0;
                    }

                    String categoriesStr = rs.getString("product_categories");
                    List<String> productCategories =
                            categoriesStr != null
                                    ? Arrays.asList(categoriesStr.split(", "))
                                    : new ArrayList<>();

                    String anomalyType;
                    if (anomalyCoefficient.compareTo(BigDecimal.valueOf(3)) > 0) {
                        anomalyType = "КРИТИЧЕСКАЯ";
                    } else if (anomalyCoefficient.compareTo(BigDecimal.valueOf(2)) > 0) {
                        anomalyType = "ВЫСОКАЯ";
                    } else {
                        anomalyType = "УМЕРЕННАЯ";
                    }

                    String possibleExplanation =
                            String.format(
                                    "Заказ превышает средний заказ пользователя в %.2f раз",
                                    anomalyCoefficient.doubleValue());

                    Boolean requiresReview =
                            anomalyCoefficient.compareTo(BigDecimal.valueOf(2)) > 0;

                    AnomalousOrderAnalytics analytics =
                            AnomalousOrderAnalytics.builder()
                                    .orderId(orderId)
                                    .userId(userId)
                                    .userEmail(userEmail)
                                    .userName(userName)
                                    .orderAmount(orderAmount)
                                    .userAverageOrderAmount(userAverageOrderAmount)
                                    .anomalyCoefficient(anomalyCoefficient)
                                    .anomalyType(anomalyType)
                                    .itemCount(itemCount)
                                    .productCategories(productCategories)
                                    .possibleExplanation(possibleExplanation)
                                    .requiresReview(requiresReview)
                                    .build();

                    anomalousOrders.add(analytics);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка поиска аномальных заказов", e);
        }
        return anomalousOrders;
    }

    @Override
    public List<UserActivitySummary> getUserActivityHistoryWithUnion(
            Long userId, LocalDate fromDate, Integer limit) throws DataAccessException {
        List<UserActivitySummary> activities = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(SQL_GET_USER_ACTIVITY_UNION)) {
            Timestamp fromTimestamp = Timestamp.valueOf(fromDate.atStartOfDay());

            statement.setLong(1, userId);
            statement.setTimestamp(2, fromTimestamp);
            statement.setLong(3, userId);
            statement.setLong(4, userId);
            statement.setLong(5, userId);
            statement.setLong(6, userId);
            statement.setInt(7, limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String activityType = rs.getString("activity_type");
                    Timestamp activityTimestamp = rs.getTimestamp("activity_date");
                    LocalDateTime activityDate =
                            activityTimestamp != null
                                    ? activityTimestamp.toLocalDateTime()
                                    : LocalDateTime.now();

                    BigDecimal activityValue = rs.getBigDecimal("activity_value");
                    String description = rs.getString("description");
                    String status = rs.getString("status");

                    Map<String, Object> additionalData = new HashMap<>();
                    additionalData.put("userId", userId);
                    if (activityType.equals("ORDER")) {
                        additionalData.put("type", "order");
                    } else if (activityType.equals("REVIEW")) {
                        additionalData.put("type", "review");
                    } else if (activityType.equals("PROMOTION")) {
                        additionalData.put("type", "promotion");
                    }

                    UserActivitySummary activity =
                            UserActivitySummary.builder()
                                    .activityType(activityType)
                                    .activityDate(activityDate)
                                    .activityValue(activityValue)
                                    .description(description)
                                    .status(status)
                                    .additionalData(additionalData)
                                    .build();

                    activities.add(activity);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка получения истории активности", e);
        }
        return activities;
    }

    @Override
    public List<SalesReportDimension> getSalesReportWithUnion(
            LocalDate reportStartDate, LocalDate reportEndDate, BigDecimal minSalesThreshold)
            throws DataAccessException {
        List<SalesReportDimension> dimensions = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(SQL_GET_SALES_REPORT_UNION)) {
            Timestamp startTimestamp = Timestamp.valueOf(reportStartDate.atStartOfDay());
            Timestamp endTimestamp = Timestamp.valueOf(reportEndDate.atTime(23, 59, 59));

            statement.setTimestamp(1, startTimestamp);
            statement.setTimestamp(2, endTimestamp);
            statement.setBigDecimal(3, minSalesThreshold);
            statement.setTimestamp(4, startTimestamp);
            statement.setTimestamp(5, endTimestamp);
            statement.setBigDecimal(6, minSalesThreshold);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String dimensionName = rs.getString("dimension_name");
                    String dimensionType = rs.getString("dimension_type");
                    BigDecimal totalSales = rs.getBigDecimal("total_sales");
                    if (totalSales == null) {
                        totalSales = BigDecimal.ZERO;
                    }

                    Integer orderCount = rs.getInt("order_count");
                    if (rs.wasNull()) {
                        orderCount = 0;
                    }

                    Integer itemCount = rs.getInt("item_count");
                    if (rs.wasNull()) {
                        itemCount = 0;
                    }

                    BigDecimal averageOrderValue = rs.getBigDecimal("avg_order_value");

                    BigDecimal growthPercentage = BigDecimal.ZERO;
                    String performanceRating;
                    if (totalSales.compareTo(BigDecimal.valueOf(100000)) > 0) {
                        performanceRating = "ОТЛИЧНО";
                    } else if (totalSales.compareTo(BigDecimal.valueOf(50000)) > 0) {
                        performanceRating = "ХОРОШО";
                    } else if (totalSales.compareTo(BigDecimal.valueOf(10000)) > 0) {
                        performanceRating = "УДОВЛЕТВОРИТЕЛЬНО";
                    } else {
                        performanceRating = "НИЗКО";
                    }

                    SalesReportDimension dimension =
                            SalesReportDimension.builder()
                                    .dimensionName(dimensionName)
                                    .dimensionType(dimensionType)
                                    .totalSales(totalSales)
                                    .growthPercentage(growthPercentage)
                                    .orderCount(orderCount)
                                    .itemCount(itemCount)
                                    .averageOrderValue(averageOrderValue)
                                    .performanceRating(performanceRating)
                                    .build();

                    dimensions.add(dimension);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка получения отчета по продажам", e);
        }
        return dimensions;
    }

    @Override
    public SubqueryPerformanceComparison compareSubqueryVsJoinPerformance(
            String customerTier, Integer monthsBack) throws DataAccessException {
        String sqlSubquery =
                String.format(SQL_COMPARE_SUBQUERY_PERFORMANCE_TEMPLATE, monthsBack, monthsBack);
        String sqlJoin = String.format(SQL_COMPARE_JOIN_PERFORMANCE_TEMPLATE, monthsBack);

        List<String> executionDetails = new ArrayList<>();
        long subqueryTime = 0;
        long joinTime = 0;
        int rowsReturned = 0;

        try (Connection conn = getConnection()) {
            // Тест подзапроса
            long startTime = System.currentTimeMillis();
            try (PreparedStatement stmt = conn.prepareStatement(sqlSubquery)) {
                stmt.setString(1, customerTier);
                stmt.setString(2, customerTier);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        rowsReturned = rs.getInt(1);
                    }
                }
            }
            subqueryTime = System.currentTimeMillis() - startTime;
            executionDetails.add(String.format("Подзапрос выполнен за %d мс", subqueryTime));

            // Тест JOIN
            startTime = System.currentTimeMillis();
            try (PreparedStatement stmt = conn.prepareStatement(sqlJoin)) {
                stmt.setString(1, customerTier);
                try (ResultSet rs = stmt.executeQuery()) {
                    // Игнорируем результат, только измеряем время
                }
            }
            joinTime = System.currentTimeMillis() - startTime;
            executionDetails.add(String.format("JOIN выполнен за %d мс", joinTime));

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка сравнения производительности", e);
        }

        long performanceDifference = Math.abs(subqueryTime - joinTime);
        double performanceRatio = joinTime > 0 ? (double) subqueryTime / joinTime : 1.0;
        String winner = subqueryTime < joinTime ? "Подзапрос" : "JOIN";
        String recommendation =
                subqueryTime < joinTime
                        ? "Использовать подзапросы для данной задачи"
                        : "Использовать JOIN для лучшей производительности";

        return SubqueryPerformanceComparison.builder()
                .comparisonMethod("Subquery vs JOIN для уровня клиента: " + customerTier)
                .subqueryExecutionTimeMs(subqueryTime)
                .joinExecutionTimeMs(joinTime)
                .performanceDifferenceMs(performanceDifference)
                .performanceRatio(performanceRatio)
                .rowsReturned(rowsReturned)
                .winner(winner)
                .recommendation(recommendation)
                .executionDetails(executionDetails)
                .build();
    }

    @Override
    public List<CategoryGrowthAnalytics> analyzeTopGrowingCategoriesWithNestedSubqueries(
            Integer analysisMonths, Integer topCategoriesLimit) throws DataAccessException {
        List<CategoryGrowthAnalytics> categories = new ArrayList<>();
        String sql =
                String.format(
                        SQL_ANALYZE_CATEGORY_GROWTH_TEMPLATE,
                        analysisMonths,
                        analysisMonths,
                        analysisMonths);

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, topCategoriesLimit);

            try (ResultSet rs = statement.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    Long categoryId = rs.getLong("category_id");
                    String categoryName = rs.getString("category_name");
                    BigDecimal totalSales = rs.getBigDecimal("total_sales");
                    if (totalSales == null) {
                        totalSales = BigDecimal.ZERO;
                    }

                    BigDecimal previousSales = rs.getBigDecimal("previous_sales");
                    if (previousSales == null) {
                        previousSales = BigDecimal.ZERO;
                    }

                    Integer orderCount = rs.getInt("order_count");
                    Integer previousOrderCount = rs.getInt("previous_order_count");

                    BigDecimal growthAmount = totalSales.subtract(previousSales);
                    BigDecimal growthPercentage = BigDecimal.ZERO;
                    if (previousSales.compareTo(BigDecimal.ZERO) > 0) {
                        growthPercentage =
                                growthAmount
                                        .divide(previousSales, 2, java.math.RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100));
                    }

                    String growthTrend;
                    if (growthPercentage.compareTo(BigDecimal.valueOf(20)) > 0) {
                        growthTrend = "БЫСТРЫЙ РОСТ";
                    } else if (growthPercentage.compareTo(BigDecimal.ZERO) > 0) {
                        growthTrend = "РОСТ";
                    } else if (growthPercentage.compareTo(BigDecimal.valueOf(-10)) > 0) {
                        growthTrend = "СТАБИЛЬНО";
                    } else {
                        growthTrend = "СНИЖЕНИЕ";
                    }

                    LocalDateTime analysisStartDate =
                            LocalDateTime.now().minusMonths(analysisMonths);
                    LocalDateTime analysisEndDate = LocalDateTime.now();

                    CategoryGrowthAnalytics analytics =
                            CategoryGrowthAnalytics.builder()
                                    .categoryId(categoryId)
                                    .categoryName(categoryName)
                                    .totalSales(totalSales)
                                    .previousPeriodSales(previousSales)
                                    .growthPercentage(growthPercentage)
                                    .growthAmount(growthAmount)
                                    .orderCount(orderCount)
                                    .previousPeriodOrderCount(previousOrderCount)
                                    .analysisStartDate(analysisStartDate)
                                    .analysisEndDate(analysisEndDate)
                                    .monthlyDetails(new ArrayList<>())
                                    .growthTrend(growthTrend)
                                    .rank(rank++)
                                    .build();

                    categories.add(analytics);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка анализа роста категорий", e);
        }
        return categories;
    }

    @Override
    public List<UpsellingOpportunity> findUpsellingOpportunitiesWithSubqueries(
            String targetCustomerTier, Integer recommendationLimit) throws DataAccessException {
        List<UpsellingOpportunity> opportunities = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement statement =
                        conn.prepareStatement(SQL_FIND_UPSELLING_OPPORTUNITIES)) {
            statement.setString(1, targetCustomerTier);
            statement.setString(2, targetCustomerTier);
            statement.setString(3, targetCustomerTier);
            statement.setString(4, targetCustomerTier);
            statement.setInt(5, recommendationLimit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Long userId = rs.getLong("id");
                    String email = rs.getString("email");
                    String firstName = rs.getString("first_name");
                    String lastName = rs.getString("last_name");
                    String currentTier = rs.getString("current_tier");
                    BigDecimal currentTotalSpent = rs.getBigDecimal("current_total_spent");
                    if (currentTotalSpent == null) {
                        currentTotalSpent = BigDecimal.ZERO;
                    }

                    BigDecimal targetTierMinimum = rs.getBigDecimal("target_tier_minimum");
                    if (targetTierMinimum == null) {
                        targetTierMinimum = BigDecimal.ZERO;
                    }

                    BigDecimal spendingGap = targetTierMinimum.subtract(currentTotalSpent);
                    if (spendingGap.compareTo(BigDecimal.ZERO) < 0) {
                        spendingGap = BigDecimal.ZERO;
                    }

                    Integer currentOrderCount = rs.getInt("current_order_count");
                    if (rs.wasNull()) {
                        currentOrderCount = 0;
                    }

                    BigDecimal averageOrderValue = rs.getBigDecimal("avg_order_value");

                    Timestamp lastPurchaseTimestamp = rs.getTimestamp("last_purchase_date");
                    LocalDateTime lastPurchaseDate =
                            lastPurchaseTimestamp != null
                                    ? lastPurchaseTimestamp.toLocalDateTime()
                                    : null;

                    BigDecimal estimatedUpsellPotential =
                            spendingGap.multiply(BigDecimal.valueOf(0.3));
                    String recommendationReason =
                            String.format(
                                    "Клиент близок к уровню %s. Не хватает %.2f для перехода",
                                    targetCustomerTier, spendingGap.doubleValue());

                    Integer confidenceScore = 75;
                    if (spendingGap.compareTo(BigDecimal.valueOf(1000)) < 0) {
                        confidenceScore = 90;
                    } else if (spendingGap.compareTo(BigDecimal.valueOf(5000)) < 0) {
                        confidenceScore = 75;
                    } else {
                        confidenceScore = 60;
                    }

                    UpsellingOpportunity opportunity =
                            UpsellingOpportunity.builder()
                                    .userId(userId)
                                    .email(email)
                                    .firstName(firstName)
                                    .lastName(lastName)
                                    .currentCustomerTier(currentTier)
                                    .targetCustomerTier(targetCustomerTier)
                                    .currentTotalSpent(currentTotalSpent)
                                    .targetTierMinimumSpent(targetTierMinimum)
                                    .spendingGap(spendingGap)
                                    .currentOrderCount(currentOrderCount)
                                    .averageOrderValue(averageOrderValue)
                                    .lastPurchaseDate(lastPurchaseDate)
                                    .recommendedProducts(new ArrayList<>())
                                    .recommendedCategories(new ArrayList<>())
                                    .estimatedUpsellPotential(estimatedUpsellPotential)
                                    .recommendationReason(recommendationReason)
                                    .confidenceScore(confidenceScore)
                                    .build();

                    opportunities.add(opportunity);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка поиска возможностей для upselling", e);
        }
        return opportunities;
    }
}
