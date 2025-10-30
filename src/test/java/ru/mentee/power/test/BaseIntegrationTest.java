/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.test;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;

@Testcontainers
public class BaseIntegrationTest implements TestDataPreparer {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("test")
                    .withUsername("root")
                    .withPassword("root")
                    .withInitScript("init.sql");

    protected ApplicationConfig config;

    @BeforeEach
    protected void setUp() throws SASTException, IOException, DataAccessException {
        // Создаем тестовую конфигурацию на основе контейнера
        config = createTestConfig();
        System.out.println("DB URL = " + config.getUrl());
        System.out.println("DB USER = " + config.getUsername());

        // Очистка данных между тестами (но не схемы!)
        cleanupAllData();
    }

    protected ApplicationConfig createTestConfig() throws SASTException, IOException {
        Properties p = new Properties();

        String jdbc = postgres.getJdbcUrl();
        String sep = jdbc.contains("?") ? "&" : "?";
        String urlWithCreds =
                jdbc
                        + sep
                        + "user="
                        + postgres.getUsername()
                        + "&password="
                        + postgres.getPassword()
                        + "&currentSchema=mentee_power";

        p.setProperty("db.url", urlWithCreds);
        p.setProperty("db.username", postgres.getUsername());
        p.setProperty("db.schema", "mentee_power");
        p.setProperty("db.driver", "org.postgresql.Driver");

        // ключевое: не загружать main-конфиги, чтобы не получить localhost
        return new ApplicationConfig(p, new ConfigFilePath()) {
            @Override
            public void load(String path) {
                /* no-op for tests */
            }
        };
    }

    protected Connection getTestConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Override
    public int createTestUsers() throws DataAccessException {
        final String sql =
                "INSERT INTO mentee_power.users (id, name, email, created_at) VALUES (?, ?, ?,"
                        + " CURRENT_TIMESTAMP)";
        try (Connection conn = getTestConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            List<String[]> data =
                    List.of(
                            new String[] {"John Doe", "john.doe@example.com"},
                            new String[] {"Jane Smith", "jane.smith@example.com"},
                            new String[] {"Alice Johnson", "alice.johnson@example.com"},
                            new String[] {"Bob Wilson", "bob.wilson@example.com"},
                            new String[] {"Charlie Brown", "charlie.brown@example.com"});

            for (String[] row : data) {
                pstmt.setObject(1, java.util.UUID.randomUUID());
                pstmt.setString(2, row[0]);
                pstmt.setString(3, row[1]);
                pstmt.addBatch();
            }
            int[] result = pstmt.executeBatch();
            return java.util.Arrays.stream(result).sum();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка создания пользователей", e);
        }
    }

    @Override
    public int createTestOrders(int ordersPerUser) throws DataAccessException {
        final String selectUsers = "SELECT id FROM mentee_power.users";
        final String selectProducts = "SELECT id FROM mentee_power.products";
        final String insertOrder =
                "INSERT INTO mentee_power.orders (id, user_id, total_price, status, order_date)"
                        + " VALUES (?, ?, ?, 'COMPLETED', CURRENT_TIMESTAMP)";
        final String insertItem =
                "INSERT INTO mentee_power.order_items (id, order_id, product_id, quantity,"
                        + " unit_price) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getTestConnection()) {
            conn.setAutoCommit(false);

            java.util.List<java.util.UUID> userIds = new java.util.ArrayList<>();
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(selectUsers)) {
                while (rs.next()) userIds.add((java.util.UUID) rs.getObject(1));
            }

            java.util.List<java.util.UUID> productIds = new java.util.ArrayList<>();
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(selectProducts)) {
                while (rs.next()) productIds.add((java.util.UUID) rs.getObject(1));
            }

            if (userIds.isEmpty()) throw new IllegalStateException("Нет пользователей");
            if (productIds.isEmpty()) throw new IllegalStateException("Нет товаров");

            int totalOrders = 0;

            try (PreparedStatement psOrder = conn.prepareStatement(insertOrder);
                    PreparedStatement psItem = conn.prepareStatement(insertItem)) {
                java.util.Random rnd = new java.util.Random(42);

                for (java.util.UUID userId : userIds) {
                    for (int i = 0; i < ordersPerUser; i++) {
                        java.util.UUID orderId = java.util.UUID.randomUUID();
                        java.math.BigDecimal total =
                                new java.math.BigDecimal(5000 + rnd.nextInt(60000)).setScale(2);

                        psOrder.setObject(1, orderId);
                        psOrder.setObject(2, userId);
                        psOrder.setBigDecimal(3, total);
                        psOrder.addBatch();

                        // 1-2 позиции в заказе
                        int items = 1 + rnd.nextInt(2);
                        for (int k = 0; k < items; k++) {
                            java.util.UUID productId =
                                    productIds.get(rnd.nextInt(productIds.size()));
                            int qty = 1 + rnd.nextInt(10);

                            BigDecimal unitPrice =
                                    BigDecimal.valueOf(100 + rnd.nextInt(9900))
                                            .movePointLeft(2)
                                            .setScale(2, java.math.RoundingMode.HALF_UP);

                            psItem.setObject(1, java.util.UUID.randomUUID());
                            psItem.setObject(2, orderId);
                            psItem.setObject(3, productId);
                            psItem.setInt(4, qty);
                            psItem.setBigDecimal(5, unitPrice);
                            psItem.addBatch();
                        }

                        totalOrders++;
                    }
                }

                psOrder.executeBatch();
                psItem.executeBatch();
            }
            conn.commit();
            return totalOrders;
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка создания заказов", e);
        }
    }

    public int createTestProducts() {
        final String sql =
                "INSERT INTO mentee_power.products (id, name, category, price) VALUES (?, ?, ?, ?)";
        try (Connection conn = getTestConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            Object[][] data =
                    new Object[][] {
                        {"Product A", "Electronics", new java.math.BigDecimal("100.00")},
                        {"Product B", "Electronics", new java.math.BigDecimal("200.00")},
                        {"Product C", "Books", new java.math.BigDecimal("50.00")},
                        {"Product D", "Clothing", new java.math.BigDecimal("150.00")}
                    };

            for (Object[] row : data) {
                ps.setObject(1, java.util.UUID.randomUUID());
                ps.setString(2, (String) row[0]);
                ps.setString(3, (String) row[1]);
                ps.setBigDecimal(4, (java.math.BigDecimal) row[2]);
                ps.addBatch();
            }
            int[] res = ps.executeBatch();
            return java.util.Arrays.stream(res).sum();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка создания товаров", e);
        }
    }

    @Override
    public void cleanupAllData() {
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO mentee_power");
            stmt.execute("DELETE FROM order_items");
            stmt.execute("DELETE FROM orders");
            stmt.execute("DELETE FROM products");
            stmt.execute("DELETE FROM users");
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка полной очистки данных", e);
        }
    }
}
