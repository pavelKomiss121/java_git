/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.entity.MonthlyOrderStats;
import ru.mentee.power.entity.OrderAnalytics;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.repository.interfaces.OrderRepository;

public class PostgresOrderRepository implements OrderRepository {

    private static final String SQL_GET_USER_ANALYTICS =
            """
    SELECT
        ROW_NUMBER() OVER (ORDER BY user_id) AS userId,
        COUNT(*) AS ordersCount,
        SUM(total_price) AS totalSpent,
        AVG(total_price) AS avgOrderValue
    FROM orders
    GROUP BY user_id
    ORDER BY totalSpent DESC
    """;

    private static final String SQL_GET_TOP_CUSTOMERS =
            """
    SELECT
        ROW_NUMBER() OVER (ORDER BY user_id) AS userId,
        COUNT(*) AS ordersCount,
        SUM(total_price) AS totalSpent,
        AVG(total_price) AS avgOrderValue
    FROM orders
    GROUP BY user_id
    ORDER BY totalSpent DESC
    LIMIT ?
    """;

    private static final String SQL_GET_MONTHLY_STATS =
            """
SELECT
    EXTRACT(YEAR FROM created_at) AS "year",
    EXTRACT(MONTH FROM created_at) AS "month",
    COUNT(*) AS ordersCount,
    SUM(total_price) AS monthlyRevenue
FROM orders
GROUP BY EXTRACT(YEAR FROM created_at), EXTRACT(MONTH FROM created_at)
ORDER BY EXTRACT(YEAR FROM created_at) DESC, EXTRACT(MONTH FROM created_at) DESC
""";

    // Column names
    private static final String COL_USER_ID = "userId";
    private static final String COL_ORDERS_COUNT = "ordersCount";
    private static final String COL_TOTAL_SPENT = "totalSpent";
    private static final String COL_AVG_ORDER_VALUE = "avgOrderValue";
    private static final String COL_YEAR = "year";
    private static final String COL_MONTH = "month";
    private static final String COL_MONTHLY_REVENUE = "monthlyRevenue";

    // Error messages
    private static final String ERROR_USER_ANALYTICS =
            "Ошибка при получении аналитики пользователей: ";
    private static final String ERROR_TOP_CUSTOMERS = "Ошибка при получении топа покупателей: ";
    private static final String ERROR_MONTHLY_STATS =
            "Ошибка при получении месячной статистики заказов: ";

    private final ApplicationConfig config;

    public PostgresOrderRepository(ApplicationConfig config) {
        this.config = config;
    }

    @Override
    public List<OrderAnalytics> getUserAnalytics() throws DataAccessException {
        List<OrderAnalytics> orderAnalyticsList = new ArrayList<>();

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(SQL_GET_USER_ANALYTICS);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BigDecimal totalSpent = resultSet.getBigDecimal(COL_TOTAL_SPENT);
                OrderAnalytics orderAnalytics =
                        OrderAnalytics.builder()
                                .userId(resultSet.getLong(COL_USER_ID))
                                .ordersCount(resultSet.getInt(COL_ORDERS_COUNT))
                                .totalSpent(totalSpent)
                                .avgOrderValue(resultSet.getBigDecimal(COL_AVG_ORDER_VALUE))
                                .customerType(OrderAnalytics.determineCustomerType(totalSpent))
                                .build();
                orderAnalyticsList.add(orderAnalytics);
            }
        } catch (SQLException e) {
            throw new DataAccessException(ERROR_USER_ANALYTICS + e.getMessage(), e);
        }
        return orderAnalyticsList;
    }

    @Override
    public List<OrderAnalytics> getTopCustomers(int limit) throws DataAccessException {
        List<OrderAnalytics> orderAnalyticsList = new ArrayList<>();

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(SQL_GET_TOP_CUSTOMERS)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    BigDecimal totalSpent = resultSet.getBigDecimal(COL_TOTAL_SPENT);
                    OrderAnalytics orderAnalytics =
                            OrderAnalytics.builder()
                                    .userId(resultSet.getLong(COL_USER_ID))
                                    .ordersCount(resultSet.getInt(COL_ORDERS_COUNT))
                                    .totalSpent(totalSpent)
                                    .avgOrderValue(resultSet.getBigDecimal(COL_AVG_ORDER_VALUE))
                                    .customerType(OrderAnalytics.determineCustomerType(totalSpent))
                                    .build();
                    orderAnalyticsList.add(orderAnalytics);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException(ERROR_TOP_CUSTOMERS + ex.getMessage(), ex);
        }
        return orderAnalyticsList;
    }

    @Override
    public List<MonthlyOrderStats> getMonthlyOrderStats() throws DataAccessException {
        List<MonthlyOrderStats> monthlyOrderStatsList = new ArrayList<>();
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(SQL_GET_MONTHLY_STATS);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BigDecimal monthlyRevenue = resultSet.getBigDecimal(COL_MONTHLY_REVENUE);
                Integer ordersCount = resultSet.getInt(COL_ORDERS_COUNT);

                MonthlyOrderStats statsObj =
                        MonthlyOrderStats.builder()
                                .year(resultSet.getInt(COL_YEAR))
                                .month(resultSet.getInt(COL_MONTH))
                                .ordersCount(ordersCount)
                                .monthlyRevenue(monthlyRevenue)
                                .avgOrderValue(
                                        monthlyRevenue.divide(
                                                BigDecimal.valueOf(ordersCount),
                                                2,
                                                RoundingMode.HALF_UP))
                                .build();
                monthlyOrderStatsList.add(statsObj);
            }

        } catch (SQLException ex) {
            throw new DataAccessException(ERROR_MONTHLY_STATS + ex.getMessage(), ex);
        }
        return monthlyOrderStatsList;
    }
}
