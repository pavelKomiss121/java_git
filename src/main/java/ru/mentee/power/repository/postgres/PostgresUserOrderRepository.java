/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.entity.ProductSalesInfo;
import ru.mentee.power.entity.UserOrderCount;
import ru.mentee.power.entity.UserOrderSummary;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.repository.UserOrderRepository;

public class PostgresUserOrderRepository implements UserOrderRepository {

    private final ApplicationConfig config;

    public PostgresUserOrderRepository(ApplicationConfig config) {
        this.config = config;
    }

    private static final String SQL_FIND_USERS =
            """
        select row_number() over (order by user_id) as user_id,
               name,
               email,
               orders_count,
               total_spend
        from users as u
        inner join
        (
          select user_id,
                count(*) as orders_count,
                sum(total_price) as total_spend
          from orders
          group by user_id
          having sum(total_price) > ?
        ) as ord on u.id = ord.user_id
""";

    private static final String SQL_GET_USERS =
            """
        select row_number() over (order by id) as user_id,
               name,
               coalesce(orders_count, 0) as orders_count,
               coalesce(total_spend,0.00) as total_spend
        from users as u
        left join
        (
        	select user_id,
        		count(*) as orders_count,
        		sum(total_price) as total_spend\s
        	from orders
        	group by user_id
        ) as ord on u.id = ord.user_id
""";

    private static final String SQL_GET_PROD =
            """
        select row_number() over (order by p.id) as product_id,
               p.name as name,
               coalesce(sum(oi.quantity), 0) as total_quantity_sold,
               count(distinct oi.order_id) as orders_count
        from products as p
        left join order_items as oi on p.id = oi.product_id
        group by p.id, p.name
        order by coalesce(sum(oi.quantity), 0) desc
        limit ?
""";

    private static final String COL_USER_ID = "user_id";
    private static final String COL_ORDERS_COUNT = "orders_count";
    private static final String COL_TOTAL_SPEND = "total_spend";
    private static final String COL_NAME = "name";
    private static final String COL_TOTAL_QUANTITY_SOLD = "total_quantity_sold";
    private static final String COL_PRODUCT_ID = "product_id";
    private static final String COL_EMAIL = "email";

    @Override
    public List<UserOrderSummary> findUsersWithTotalAbove(BigDecimal minTotal)
            throws DataAccessException {

        List<UserOrderSummary> userOrderSummaries = new ArrayList<>();

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement ps = connection.prepareStatement(SQL_FIND_USERS); ) {
            ps.setBigDecimal(1, minTotal);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserOrderSummary summary =
                            UserOrderSummary.builder()
                                    .userId(rs.getLong(COL_USER_ID))
                                    .userName(rs.getString(COL_NAME))
                                    .email(rs.getString(COL_EMAIL))
                                    .ordersCount(rs.getInt(COL_ORDERS_COUNT))
                                    .totalSpent(rs.getBigDecimal(COL_TOTAL_SPEND))
                                    .build();
                    userOrderSummaries.add(summary);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException(
                    "Ошибка поиска пользователей с общей суммой заказов больше указанной", ex);
        }
        return userOrderSummaries;
    }

    @Override
    public List<UserOrderCount> getAllUsersWithOrderCount() throws DataAccessException {
        List<UserOrderCount> userOrderCounts = new ArrayList<>();
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement ps = connection.prepareStatement(SQL_GET_USERS);
                ResultSet rs = ps.executeQuery(); ) {
            while (rs.next()) {
                UserOrderCount user =
                        UserOrderCount.builder()
                                .userId(rs.getLong(COL_USER_ID))
                                .userName(rs.getString(COL_NAME))
                                .ordersCount(rs.getInt(COL_ORDERS_COUNT))
                                .totalSpent(rs.getBigDecimal(COL_TOTAL_SPEND))
                                .build();
                userOrderCounts.add(user);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка получения пользователей", ex);
        }
        return userOrderCounts;
    }

    @Override
    public List<ProductSalesInfo> getTopSellingProducts(int limit) throws DataAccessException {
        List<ProductSalesInfo> productSalesInfo = new ArrayList<>();

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement ps = connection.prepareStatement(SQL_GET_PROD)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProductSalesInfo product =
                            ProductSalesInfo.builder()
                                    .productId(rs.getLong(COL_PRODUCT_ID))
                                    .productName(rs.getString(COL_NAME))
                                    .totalOrdersCount(rs.getLong(COL_ORDERS_COUNT))
                                    .totalQuantitySold(rs.getLong(COL_TOTAL_QUANTITY_SOLD))
                                    .build();
                    productSalesInfo.add(product);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка в получении товаров", ex);
        }
        return productSalesInfo;
    }
}
