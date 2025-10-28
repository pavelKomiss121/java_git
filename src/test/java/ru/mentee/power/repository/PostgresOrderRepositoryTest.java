/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;
import ru.mentee.power.entity.MonthlyOrderStats;
import ru.mentee.power.entity.OrderAnalytics;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;

public class PostgresOrderRepositoryTest {

    private PostgresOrderRepository repository;
    private ApplicationConfig config;

    @BeforeEach
    void setUp() throws IOException, SASTException {
        Properties properties = loadTestProperties();

        this.config =
                new ApplicationConfig(properties, new ConfigFilePath()) {
                    @Override
                    public void load(String path) {}
                };

        repository = new PostgresOrderRepository(config);
        initializeTestDatabase();
    }

    @AfterEach
    void tearDown() {
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                Statement statement = connection.createStatement()) {

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

    @Test
    void getUserAnalytics_ShouldGroupOrdersByUser() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getUserAnalytics();

        assertThat(analytics).isNotEmpty();

        analytics.forEach(
                analytic -> {
                    assertThat(analytic.getUserId()).isNotNull();
                    assertThat(analytic.getOrdersCount()).isGreaterThan(0);
                    assertThat(analytic.getTotalSpent()).isNotNull();
                    assertThat(analytic.getAvgOrderValue()).isNotNull();
                    assertThat(analytic.getCustomerType()).isIn("VIP", "REGULAR", "NEW");
                });
    }

    @Test
    void getUserAnalytics_ShouldCalculateAggregatesCorrectly() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getUserAnalytics();

        assertThat(analytics).isNotEmpty();

        for (OrderAnalytics analytic : analytics) {
            BigDecimal expectedAvg =
                    analytic.getTotalSpent()
                            .divide(
                                    BigDecimal.valueOf(analytic.getOrdersCount()),
                                    2,
                                    java.math.RoundingMode.HALF_UP);

            BigDecimal actualAvg =
                    analytic.getAvgOrderValue().setScale(2, java.math.RoundingMode.HALF_UP);

            assertThat(actualAvg).isEqualTo(expectedAvg);
        }
    }

    @Test
    void getUserAnalytics_ShouldDetermineCustomerTypeCorrectly() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getUserAnalytics();

        assertThat(analytics).isNotEmpty();

        for (OrderAnalytics analytic : analytics) {
            BigDecimal totalSpent = analytic.getTotalSpent();
            String expectedType = OrderAnalytics.determineCustomerType(totalSpent);

            assertThat(analytic.getCustomerType()).isEqualTo(expectedType);

            if (totalSpent.compareTo(new BigDecimal("50000")) > 0) {
                assertThat(analytic.getCustomerType()).isEqualTo("VIP");
            } else if (totalSpent.compareTo(new BigDecimal("10000")) >= 0) {
                assertThat(analytic.getCustomerType()).isEqualTo("REGULAR");
            } else {
                assertThat(analytic.getCustomerType()).isEqualTo("NEW");
            }
        }
    }

    @Test
    void getUserAnalytics_ShouldNotIncludeUsersWithoutOrders() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getUserAnalytics();

        analytics.forEach(
                analytic -> {
                    assertThat(analytic.getOrdersCount()).isGreaterThan(0);
                });
    }

    @Test
    void getTopCustomers_ShouldLimitResults() throws DataAccessException {
        int limit = 3;
        List<OrderAnalytics> analytics = repository.getTopCustomers(limit);

        assertThat(analytics).isNotEmpty();
        assertThat(analytics.size()).isLessThanOrEqualTo(limit);
    }

    @Test
    void getTopCustomers_ShouldSortByTotalSpentDescending() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getTopCustomers(10);

        assertThat(analytics).isNotEmpty();

        for (int i = 0; i < analytics.size() - 1; i++) {
            BigDecimal currentTotal = analytics.get(i).getTotalSpent();
            BigDecimal nextTotal = analytics.get(i + 1).getTotalSpent();

            assertThat(currentTotal).isGreaterThanOrEqualTo(nextTotal);
        }
    }

    @Test
    void getTopCustomers_ShouldReturnMostActiveCustomersFirst() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getTopCustomers(5);

        assertThat(analytics).isNotEmpty();

        BigDecimal firstTotalSpent = analytics.get(0).getTotalSpent();
        analytics.forEach(
                analytic -> {
                    assertThat(analytic.getTotalSpent()).isLessThanOrEqualTo(firstTotalSpent);
                });
    }

    @Test
    void getMonthlyOrderStats_ShouldGroupByYearAndMonth() throws DataAccessException {
        List<MonthlyOrderStats> stats = repository.getMonthlyOrderStats();

        assertThat(stats).isNotEmpty();

        stats.forEach(
                stat -> {
                    assertThat(stat.getYear()).isNotNull();
                    assertThat(stat.getMonth()).isNotNull();
                    assertThat(stat.getMonth()).isBetween(1, 12);
                    assertThat(stat.getOrdersCount()).isGreaterThan(0);
                    assertThat(stat.getMonthlyRevenue()).isNotNull();
                    assertThat(stat.getAvgOrderValue()).isNotNull();
                });
    }

    @Test
    void getMonthlyOrderStats_ShouldSortByYearAndMonthDescending() throws DataAccessException {
        List<MonthlyOrderStats> stats = repository.getMonthlyOrderStats();

        assertThat(stats).isNotEmpty();

        for (int i = 0; i < stats.size() - 1; i++) {
            MonthlyOrderStats current = stats.get(i);
            MonthlyOrderStats next = stats.get(i + 1);

            assertThat(current.getYear()).isGreaterThanOrEqualTo(next.getYear());

            if (current.getYear().equals(next.getYear())) {
                assertThat(current.getMonth()).isGreaterThanOrEqualTo(next.getMonth());
            }
        }
    }

    @Test
    void getMonthlyOrderStats_ShouldCalculateAvgOrderValueCorrectly() throws DataAccessException {
        List<MonthlyOrderStats> stats = repository.getMonthlyOrderStats();

        assertThat(stats).isNotEmpty();

        for (MonthlyOrderStats stat : stats) {
            BigDecimal expectedAvg =
                    stat.getMonthlyRevenue()
                            .divide(
                                    BigDecimal.valueOf(stat.getOrdersCount()),
                                    2,
                                    java.math.RoundingMode.HALF_UP);

            assertThat(stat.getAvgOrderValue()).isEqualTo(expectedAvg);
        }
    }

    @Test
    void getUserAnalytics_WithEmptyDatabase_ShouldReturnEmptyList() throws DataAccessException {

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM orders");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка очистки БД", e);
        }

        List<OrderAnalytics> analytics = repository.getUserAnalytics();

        assertThat(analytics).isEmpty();
    }

    @Test
    void getTopCustomers_WithZeroLimit_ShouldReturnEmptyList() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getTopCustomers(0);

        assertThat(analytics).isEmpty();
    }

    @Test
    void getMonthlyOrderStats_WithEmptyDatabase_ShouldReturnEmptyList() throws DataAccessException {

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM orders");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка очистки БД", e);
        }

        List<MonthlyOrderStats> stats = repository.getMonthlyOrderStats();

        assertThat(stats).isEmpty();
    }

    @Test
    void getAllMethods_ShouldHandleNullValuesGracefully() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getUserAnalytics();

        analytics.forEach(
                analytic -> {
                    assertThatCode(
                                    () -> {
                                        analytic.getUserId();
                                        analytic.getOrdersCount();
                                        analytic.getTotalSpent();
                                        analytic.getAvgOrderValue();
                                        analytic.getCustomerType();
                                    })
                            .doesNotThrowAnyException();
                });
    }

    @Test
    void getAllMethods_ShouldCloseResourcesProperly() throws DataAccessException {
        assertThatCode(
                        () -> {
                            repository.getUserAnalytics();
                            repository.getTopCustomers(5);
                            repository.getMonthlyOrderStats();
                        })
                .doesNotThrowAnyException();

        repository.getUserAnalytics();
        repository.getTopCustomers(5);
        repository.getMonthlyOrderStats();
    }

    @Test
    void getTopCustomers_WithNegativeLimit_ShouldThrowException() {
        assertThatThrownBy(() -> repository.getTopCustomers(-1))
                .isInstanceOf(DataAccessException.class);
    }
}
