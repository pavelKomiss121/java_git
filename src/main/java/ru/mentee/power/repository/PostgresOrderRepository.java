/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.entity.MonthlyOrderStats;
import ru.mentee.power.entity.OrderAnalytics;
import ru.mentee.power.exception.DataAccessException;

public class PostgresOrderRepository implements OrderRepository {

    private final ApplicationConfig config;

    public PostgresOrderRepository(ApplicationConfig config) {
        this.config = config;
    }

    @Override
    public List<OrderAnalytics> getUserAnalytics() throws DataAccessException {
        String sql =
                """
        SELECT
            user_id AS userId,
            COUNT(*) AS ordersCount,
            SUM(total_price) AS totalSpent,
            AVG(total_price) AS avgOrderValue
        FROM orders
        GROUP BY user_id
        ORDER BY totalSpent DESC
    """;
        List<OrderAnalytics> orderAnalyticsList = new ArrayList<>();

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BigDecimal totalSpent = resultSet.getBigDecimal("totalSpent");
                OrderAnalytics orderAnalytics =
                        OrderAnalytics.builder()
                                .userId(resultSet.getLong("userId"))
                                .ordersCount(resultSet.getInt("ordersCount"))
                                .totalSpent(totalSpent)
                                .avgOrderValue(resultSet.getBigDecimal("avgOrderValue"))
                                .customerType(OrderAnalytics.determineCustomerType(totalSpent))
                                .build();
                orderAnalyticsList.add(orderAnalytics);
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Ошибка при получении аналитики пользователей: " + e.getMessage(), e);
        }
        return orderAnalyticsList;
    }

    @Override
    public List<OrderAnalytics> getTopCustomers(int limit) throws DataAccessException {
        String sql =
                """
        SELECT
            user_id AS userId,
            COUNT(*) AS ordersCount,
            SUM(total_price) AS totalSpent,
            AVG(total_price) AS avgOrderValue
        FROM orders
        GROUP BY user_id
        ORDER BY totalSpent DESC
        LIMIT ?
    """;

        List<OrderAnalytics> orderAnalyticsList = new ArrayList<>();

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    BigDecimal totalSpent = resultSet.getBigDecimal("totalSpent");
                    OrderAnalytics orderAnalytics =
                            OrderAnalytics.builder()
                                    .userId(resultSet.getLong("userId"))
                                    .ordersCount(resultSet.getInt("ordersCount"))
                                    .totalSpent(totalSpent)
                                    .avgOrderValue(resultSet.getBigDecimal("avgOrderValue"))
                                    .customerType(OrderAnalytics.determineCustomerType(totalSpent))
                                    .build();
                    orderAnalyticsList.add(orderAnalytics);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException(
                    "Ошибка при получении топа покупателей: " + ex.getMessage(), ex);
        }
        return orderAnalyticsList;
    }

    @Override
    public List<MonthlyOrderStats> getMonthlyOrderStats() throws DataAccessException {
        String sql =
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
        List<MonthlyOrderStats> monthlyOrderStatsList = new ArrayList<>();
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BigDecimal monthlyRevenue = resultSet.getBigDecimal("monthlyRevenue");
                Integer ordersCount = resultSet.getInt("ordersCount");

                MonthlyOrderStats statsObj =
                        MonthlyOrderStats.builder()
                                .year(resultSet.getInt("year"))
                                .month(resultSet.getInt("month"))
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
            throw new DataAccessException(
                    "Ошибка при получении месячной статистики заказов: " + ex.getMessage(), ex);
        }
        return monthlyOrderStatsList;
    }
}
