/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mentee.power.entity.MonthlyOrderStats;
import ru.mentee.power.entity.OrderAnalytics;
import ru.mentee.power.entity.ProductSalesInfo;
import ru.mentee.power.entity.UserOrderCount;
import ru.mentee.power.entity.UserOrderSummary;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.repository.postgres.PostgresOrderRepository;
import ru.mentee.power.repository.postgres.PostgresUserOrderRepository;
import ru.mentee.power.test.BaseIntegrationTest;

@Disabled("Only Liquibase tests should run")
@DisplayName("Интеграционное тестирование OrderRepository с TestContainers")
class OrderRepositoryIntegrationTest extends BaseIntegrationTest {

    private PostgresOrderRepository repository;

    @BeforeEach
    protected void setUp() throws DataAccessException, SASTException, IOException {
        super.setUp(); // обязательно, чтобы инициализировать config и контейнер
        cleanupAllData(); // если нужно чистить после super.setUp()
        createTestUsers();
        createTestProducts();
        createTestOrders(2);
        repository = new PostgresOrderRepository(config);
    }

    @Test
    @DisplayName("Should получить аналитику пользователей с агрегацией")
    void shouldGetUserAnalytics() throws DataAccessException {
        List<OrderAnalytics> analytics = repository.getUserAnalytics();

        assertThat(analytics).isNotEmpty();
        analytics.forEach(
                a -> {
                    assertThat(a.getUserId()).isNotNull();
                    assertThat(a.getOrdersCount()).isGreaterThan(0);
                    assertThat(a.getTotalSpent()).isNotNull();
                    assertThat(a.getAvgOrderValue()).isNotNull();
                    assertThat(a.getCustomerType()).isIn("VIP", "REGULAR", "NEW");
                });

        for (int i = 0; i < analytics.size() - 1; i++) {
            assertThat(analytics.get(i).getTotalSpent())
                    .isGreaterThanOrEqualTo(analytics.get(i + 1).getTotalSpent());
        }
    }

    @Test
    @DisplayName("Should найти топ покупателей с лимитом")
    void shouldGetTopCustomers() throws DataAccessException {
        int limit = 5;
        List<OrderAnalytics> top = repository.getTopCustomers(limit);

        assertThat(top).isNotEmpty();
        assertThat(top.size()).isLessThanOrEqualTo(limit);

        for (int i = 0; i < top.size() - 1; i++) {
            assertThat(top.get(i).getTotalSpent())
                    .isGreaterThanOrEqualTo(top.get(i + 1).getTotalSpent());
        }
    }

    @Test
    @DisplayName("Should получить месячную статистику заказов")
    void shouldGetMonthlyOrderStats() throws DataAccessException {
        List<MonthlyOrderStats> stats = repository.getMonthlyOrderStats();

        assertThat(stats).isNotEmpty();
        stats.forEach(
                s -> {
                    assertThat(s.getYear()).isNotNull();
                    assertThat(s.getMonth()).isBetween(1, 12);
                    assertThat(s.getOrdersCount()).isGreaterThan(0);
                    assertThat(s.getMonthlyRevenue()).isNotNull();
                    assertThat(s.getAvgOrderValue()).isNotNull();
                });

        // Проверим порядок сортировки (год, затем месяц по убыванию)
        for (int i = 0; i < stats.size() - 1; i++) {
            MonthlyOrderStats cur = stats.get(i);
            MonthlyOrderStats next = stats.get(i + 1);
            assertThat(cur.getYear()).isGreaterThanOrEqualTo(next.getYear());
            if (cur.getYear().equals(next.getYear())) {
                assertThat(cur.getMonth()).isGreaterThanOrEqualTo(next.getMonth());
            }
        }
    }

    @Test
    @DisplayName("Should вернуть пустую аналитику при отсутствии заказов")
    void shouldReturnEmptyAnalyticsWhenNoOrders() throws DataAccessException {
        // Подготовка: очистим только заказы, пользователи останутся
        cleanupAllData();
        createTestUsers();
        repository = new PostgresOrderRepository(config);

        List<OrderAnalytics> analytics = repository.getUserAnalytics();
        assertThat(analytics).isEmpty();
    }

    @Test
    @DisplayName("Should работать с большим объемом данных")
    void shouldHandleLargeDataSet() throws DataAccessException {
        cleanupAllData();
        createTestUsers();
        createTestProducts();
        // создадим побольше заказов
        createTestOrders(10);
        repository = new PostgresOrderRepository(config);

        long start = System.currentTimeMillis();
        List<OrderAnalytics> analytics = repository.getUserAnalytics();
        List<MonthlyOrderStats> stats = repository.getMonthlyOrderStats();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(analytics).isNotEmpty();
        assertThat(stats).isNotEmpty();
        // эвристический лимит производительности для интеграционного теста
        assertThat(elapsed).isLessThan(5_000);
    }
}

@Disabled("Only Liquibase tests should run")
@DisplayName("Интеграционное тестирование UserOrderRepository с TestContainers")
class UserOrderRepositoryIntegrationTest extends BaseIntegrationTest {

    private PostgresUserOrderRepository repository;

    @BeforeEach
    protected void setUp() throws DataAccessException, SASTException, IOException {
        super.setUp();
        cleanupAllData();
        createTestUsers();
        createTestProducts();
        createTestOrders(2);
        repository = new PostgresUserOrderRepository(config);
    }

    @Test
    @DisplayName("Should выполнить INNER JOIN и найти пользователей с заказами")
    void shouldFindUsersWithTotalAboveUsingInnerJoin() throws DataAccessException {
        BigDecimal minTotal = new BigDecimal("1000");

        List<UserOrderSummary> result = repository.findUsersWithTotalAbove(minTotal);

        assertThat(result).isNotEmpty();
        // Проверяем, что в результате нет пользователей без заказов
        result.forEach(
                r -> {
                    assertThat(r.getOrdersCount()).isGreaterThan(0);
                    assertThat(r.getTotalSpent()).isGreaterThan(minTotal);
                });
        // Уникальность по пользователю (GROUP BY корректен)
        long distinctUsers = result.stream().map(UserOrderSummary::getUserId).distinct().count();
        assertThat(distinctUsers).isEqualTo(result.size());
    }

    @Test
    @DisplayName("Should выполнить LEFT JOIN и включить всех пользователей")
    void shouldGetAllUsersWithOrderCountUsingLeftJoin() throws DataAccessException {
        cleanupAllData();
        createTestUsers();
        createTestProducts();
        createTestOrders(1);
        try (var conn = getTestConnection();
                var st = conn.createStatement()) {
            st.execute(
                    """
                            DELETE FROM mentee_power.order_items WHERE order_id IN (
                            SELECT id FROM mentee_power.orders WHERE user_id IN (SELECT id FROM mentee_power.users ORDER BY id LIMIT 2)
                             )
                        """);
            st.execute(
                    "DELETE FROM mentee_power.orders WHERE user_id IN (SELECT id FROM"
                            + " mentee_power.users ORDER BY id LIMIT 2)");
        } catch (Exception ignored) {
        }
        repository = new PostgresUserOrderRepository(config);

        List<UserOrderCount> result = repository.getAllUsersWithOrderCount();

        assertThat(result).isNotEmpty();
        assertThat(result.size()).isGreaterThanOrEqualTo(5);
        boolean hasZeroOrders = result.stream().anyMatch(u -> u.getOrdersCount() == 0);
        assertThat(hasZeroOrders).isTrue();
        result.forEach(
                u -> {
                    assertThat(u.getUserName()).isNotBlank();
                    assertThat(u.getTotalSpent()).isNotNull();
                });
    }

    @Test
    @DisplayName("Should выполнить многотабличный JOIN для товаров")
    void shouldGetTopSellingProductsUsingMultiTableJoin() throws DataAccessException {
        int limit = 10;
        List<ProductSalesInfo> products = repository.getTopSellingProducts(limit);

        assertThat(products).isNotEmpty();
        assertThat(products.size()).isLessThanOrEqualTo(limit);

        // Проверим сортировку по количеству проданных единиц по убыванию
        for (int i = 0; i < products.size() - 1; i++) {
            assertThat(products.get(i).getTotalQuantitySold())
                    .isGreaterThanOrEqualTo(products.get(i + 1).getTotalQuantitySold());
        }
    }

    @Test
    @DisplayName("Should корректно обработать NULL в LEFT JOIN ResultSet")
    void shouldHandleNullInLeftJoinResultSet() throws DataAccessException {
        cleanupAllData();
        createTestUsers();
        repository = new PostgresUserOrderRepository(config);

        List<UserOrderCount> result = repository.getAllUsersWithOrderCount();
        assertThat(result).isNotEmpty();
        // Должны присутствовать пользователи с нулевым количеством заказов и 0 суммой
        result.forEach(
                u -> {
                    assertThat(u.getOrdersCount()).isNotNull();
                    assertThat(u.getTotalSpent()).isNotNull();
                    if (u.getOrdersCount() == 0) {
                        assertThat(u.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
                    }
                });
    }

    @Test
    @DisplayName("Should выдать корректные ошибки при нарушении FK constraints")
    void shouldHandleForeignKeyViolations() {
        assertThatThrownBy(
                        () -> {
                            try (var conn = getTestConnection();
                                    var ps =
                                            conn.prepareStatement(
                                                    "INSERT INTO mentee_power.orders (id, user_id,"
                                                            + " total_price, status) VALUES (?,?,?,"
                                                            + " 'COMPLETED')")) {
                                ps.setObject(1, java.util.UUID.randomUUID());
                                ps.setObject(2, java.util.UUID.randomUUID());
                                ps.setBigDecimal(3, new BigDecimal("100"));
                                ps.executeUpdate();
                            }
                        })
                .isInstanceOf(Exception.class);
    }
}
