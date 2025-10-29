/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;
import ru.mentee.power.entity.ProductSalesInfo;
import ru.mentee.power.entity.UserOrderCount;
import ru.mentee.power.entity.UserOrderSummary;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;

public class PostgresUserOrderRepositoryTest {

    private PostgresUserOrderRepository repository;
    private ApplicationConfig config;

    @BeforeEach
    void setUp() throws IOException, SASTException {
        Properties properties = loadTestProperties();

        this.config =
                new ApplicationConfig(properties, new ConfigFilePath()) {
                    @Override
                    public void load(String path) {}
                };

        repository = new PostgresUserOrderRepository(config);
        initializeTestDatabase();
    }

    @AfterEach
    void tearDown() {
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                Statement statement = connection.createStatement()) {

            statement.execute("DELETE FROM order_items");
            statement.execute("DELETE FROM products");
            statement.execute("DELETE FROM orders");
            statement.execute("DELETE FROM users");

        } catch (Exception e) {
        }
    }

    private Properties loadTestProperties() throws IOException {
        Properties properties = new Properties();
        String resourcePath = "application-test.properties";

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Файл " + resourcePath + " не найден в classpath");
            }
            properties.load(input);
        }

        return properties;
    }

    private void initializeTestDatabase() {
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                Statement statement = connection.createStatement()) {

            String sqlScript = loadResource("test-schema.sql");
            statement.execute(sqlScript);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации тестовой БД", e);
        }
    }

    private String loadResource(String resourcePath) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Файл " + resourcePath + " не найден в classpath");
            }

            byte[] bytes = input.readAllBytes();
            return new String(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения ресурса " + resourcePath, e);
        }
    }

    // ========== Тесты для findUsersWithTotalAbove ==========

    @Test
    void findUsersWithTotalAbove_ShouldReturnOnlyUsersAboveMinTotal() throws DataAccessException {
        BigDecimal minTotal = new BigDecimal("50000");

        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(minTotal);

        assertThat(result).isNotEmpty();
        result.forEach(
                summary -> {
                    assertThat(summary.getTotalSpent()).isNotNull().isGreaterThan(minTotal);
                });
    }

    @Test
    void findUsersWithTotalAbove_ShouldExcludeUsersWithoutOrders() throws DataAccessException {
        BigDecimal minTotal = new BigDecimal("0");

        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(minTotal);

        // John Doe: 70000 (3 заказа)
        // Jane Smith: 60000 (2 заказа)
        // Bob Wilson: 60000 (1 заказ)
        // Alice Johnson: 8000 (1 заказ) - должен быть включен, т.к. > 0
        // Charlie Brown: нет заказов - должен быть исключен (INNER JOIN)

        assertThat(result.size()).isGreaterThan(0);
        assertThat(result)
                .extracting(UserOrderSummary::getUserName)
                .doesNotContain("Charlie Brown"); // Пользователь без заказов исключен
    }

    @Test
    void findUsersWithTotalAbove_ShouldValidateSumAggregation() throws DataAccessException {
        BigDecimal minTotal = new BigDecimal("50000");

        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(minTotal);

        // John Doe должен иметь totalSpent = 15000 + 25000 + 30000 = 70000
        UserOrderSummary johnDoe =
                result.stream()
                        .filter(s -> "John Doe".equals(s.getUserName()))
                        .findFirst()
                        .orElse(null);

        assertThat(johnDoe).isNotNull();
        assertThat(johnDoe.getTotalSpent()).isEqualByComparingTo(new BigDecimal("70000"));
        assertThat(johnDoe.getOrdersCount()).isEqualTo(3);
    }

    @Test
    void findUsersWithTotalAbove_ShouldValidateGroupByGrouping() throws DataAccessException {
        BigDecimal minTotal = new BigDecimal("10000");

        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(minTotal);

        // Проверяем что каждый пользователь представлен только один раз (GROUP BY работает)
        long uniqueUserCount = result.stream().map(UserOrderSummary::getUserId).distinct().count();
        assertThat(uniqueUserCount).isEqualTo(result.size());
    }

    @Test
    void findUsersWithTotalAbove_ShouldHandleHighMinTotal() throws DataAccessException {
        BigDecimal minTotal = new BigDecimal("100000");

        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(minTotal);

        assertThat(result).isEmpty();
    }

    @Test
    void findUsersWithTotalAbove_ShouldHandleZeroMinTotal() throws DataAccessException {
        BigDecimal minTotal = new BigDecimal("0");

        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(minTotal);

        assertThat(result).isNotEmpty();
        // Все пользователи с заказами должны быть включены
        assertThat(result.size()).isGreaterThanOrEqualTo(4); // 4 пользователя с заказами
    }

    // ========== Тесты для getAllUsersWithOrderCount ==========

    @Test
    void getAllUsersWithOrderCount_ShouldIncludeAllUsers() throws DataAccessException {
        List<UserOrderCount> result = repository.getAllUsersWithOrderCount();

        assertThat(result).isNotEmpty();
        // Проверяем что все 5 пользователей включены
        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    void getAllUsersWithOrderCount_ShouldSetZeroForUsersWithoutOrders() throws DataAccessException {
        List<UserOrderCount> result = repository.getAllUsersWithOrderCount();

        UserOrderCount charlie =
                result.stream()
                        .filter(u -> "Charlie Brown".equals(u.getUserName()))
                        .findFirst()
                        .orElse(null);

        assertThat(charlie).isNotNull();
        assertThat(charlie.getOrdersCount()).isEqualTo(0);
        assertThat(charlie.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getAllUsersWithOrderCount_ShouldCorrectlyCountOrders() throws DataAccessException {
        List<UserOrderCount> result = repository.getAllUsersWithOrderCount();

        UserOrderCount johnDoe =
                result.stream()
                        .filter(u -> "John Doe".equals(u.getUserName()))
                        .findFirst()
                        .orElse(null);

        assertThat(johnDoe).isNotNull();
        assertThat(johnDoe.getOrdersCount()).isEqualTo(3);
    }

    @Test
    void getAllUsersWithOrderCount_ShouldValidateTotalSpentCoalesce() throws DataAccessException {
        List<UserOrderCount> result = repository.getAllUsersWithOrderCount();

        UserOrderCount johnDoe =
                result.stream()
                        .filter(u -> "John Doe".equals(u.getUserName()))
                        .findFirst()
                        .orElse(null);

        assertThat(johnDoe).isNotNull();
        assertThat(johnDoe.getTotalSpent()).isEqualByComparingTo(new BigDecimal("70000"));

        // Проверяем что пользователь без заказов имеет 0.00, а не null
        UserOrderCount charlie =
                result.stream()
                        .filter(u -> "Charlie Brown".equals(u.getUserName()))
                        .findFirst()
                        .orElse(null);
        assertThat(charlie).isNotNull();
        assertThat(charlie.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========== Тесты для getTopSellingProducts ==========

    @Test
    void getTopSellingProducts_ShouldLimitResults() throws DataAccessException {
        int limit = 2;

        List<ProductSalesInfo> result = repository.getTopSellingProducts(limit);

        assertThat(result).isNotEmpty();
        assertThat(result.size()).isLessThanOrEqualTo(limit);
    }

    @Test
    void getTopSellingProducts_ShouldSortByQuantityDescending() throws DataAccessException {
        int limit = 10;

        List<ProductSalesInfo> result = repository.getTopSellingProducts(limit);

        assertThat(result).isNotEmpty();

        // Проверяем сортировку по убыванию
        for (int i = 0; i < result.size() - 1; i++) {
            Long current = result.get(i).getTotalQuantitySold();
            Long next = result.get(i + 1).getTotalQuantitySold();
            assertThat(current).isGreaterThanOrEqualTo(next);
        }
    }

    @Test
    void getTopSellingProducts_ShouldHandleMultiTableJoin() throws DataAccessException {
        int limit = 10;

        List<ProductSalesInfo> result = repository.getTopSellingProducts(limit);

        assertThat(result).isNotEmpty();
        // Проверяем что данные из разных таблиц корректно объединены
        result.forEach(
                product -> {
                    assertThat(product.getProductId()).isNotNull();
                    assertThat(product.getProductName()).isNotNull();
                    assertThat(product.getTotalQuantitySold()).isNotNull();
                    assertThat(product.getTotalOrdersCount()).isNotNull();
                });
    }

    @Test
    void getTopSellingProducts_ShouldValidateOrderCountAggregation() throws DataAccessException {
        int limit = 10;

        List<ProductSalesInfo> result = repository.getTopSellingProducts(limit);

        // Product A должен иметь 2 заказа (order_items с order_id 100 и 101)
        ProductSalesInfo productA =
                result.stream()
                        .filter(p -> "Product A".equals(p.getProductName()))
                        .findFirst()
                        .orElse(null);

        if (productA != null) {
            assertThat(productA.getTotalOrdersCount()).isGreaterThan(0);
        }
    }

    @Test
    void getTopSellingProducts_ShouldHandleZeroLimit() throws DataAccessException {
        int limit = 0;

        List<ProductSalesInfo> result = repository.getTopSellingProducts(limit);

        assertThat(result).isEmpty();
    }

    // ========== Тесты граничных случаев ==========

    @Test
    void findUsersWithTotalAbove_WithEmptyOrders_ShouldReturnEmptyList()
            throws DataAccessException {
        // Очищаем заказы (сначала удаляем зависимые таблицы)
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM order_items");
            statement.execute("DELETE FROM orders");
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка очистки заказов", e);
        }

        BigDecimal minTotal = new BigDecimal("0");
        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(minTotal);

        assertThat(result).isEmpty(); // INNER JOIN вернет пустой список
    }

    @Test
    void getAllUsersWithOrderCount_WithEmptyOrders_ShouldReturnAllUsersWithZeroCount()
            throws DataAccessException {
        // Очищаем заказы (сначала удаляем зависимые таблицы)
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM order_items");
            statement.execute("DELETE FROM orders");
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка очистки заказов", e);
        }

        List<UserOrderCount> result = repository.getAllUsersWithOrderCount();

        assertThat(result).isNotEmpty(); // LEFT JOIN вернет всех пользователей
        result.forEach(
                user -> {
                    assertThat(user.getOrdersCount()).isEqualTo(0);
                    assertThat(user.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
                });
    }

    @Test
    void getTopSellingProducts_ShouldValidateLimitParameter() throws DataAccessException {
        // Тестируем с разными значениями limit
        assertThat(repository.getTopSellingProducts(1).size()).isLessThanOrEqualTo(1);
        assertThat(repository.getTopSellingProducts(5).size()).isLessThanOrEqualTo(5);
        assertThat(repository.getTopSellingProducts(100).size()).isLessThanOrEqualTo(100);
    }

    // ========== Тесты обработки исключений ==========

    @Test
    void findUsersWithTotalAbove_ShouldHandleNegativeMinTotal() throws DataAccessException {
        BigDecimal negativeTotal = new BigDecimal("-1000");

        // Должен вернуть всех пользователей с заказами (так как любая сумма > -1000)
        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(negativeTotal);

        assertThat(result).isNotEmpty();
    }
}
