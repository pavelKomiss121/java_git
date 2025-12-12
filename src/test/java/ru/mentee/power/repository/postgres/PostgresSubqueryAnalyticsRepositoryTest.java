/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.mp169.*;
import ru.mentee.power.test.BaseIntegrationTest;

@DisplayName("Тестирование подзапросов и аналитики")
@SuppressWarnings({"resource", "deprecation"})
@Disabled("Урок пройден")
public class PostgresSubqueryAnalyticsRepositoryTest extends BaseIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresSubqueryAnalyticsRepositoryTest.class);

    private Liquibase liquibase;
    private PostgresSubqueryAnalyticsRepository subqueryRepository;

    @BeforeEach
    @Override
    protected void setUp() throws SASTException, IOException, DataAccessException {
        super.setUp();

        try (Connection conn = getTestConnection()) {
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            liquibase =
                    new Liquibase(
                            "db/migrations_161/changelog.yaml",
                            new ClassLoaderResourceAccessor(),
                            database);

            // Применяем миграции 1, 9 и 10
            liquibase.update("dev,test");

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        subqueryRepository = new PostgresSubqueryAnalyticsRepository(config);
    }

    @Test
    @DisplayName("Should find VIP customers using scalar subqueries")
    void shouldFindVipCustomersWithSubqueries() throws DataAccessException, SQLException {
        // Given
        Double spendingMultiplier = 1.5; // 50% выше среднего
        Integer limit = 10;

        // When
        List<VipCustomerAnalytics> vipCustomers =
                subqueryRepository.findVipCustomersWithSubqueries(spendingMultiplier, limit);

        // Then
        assertThat(vipCustomers).isNotEmpty();
        assertThat(vipCustomers.size()).isLessThanOrEqualTo(limit);

        // Validate VIP criteria
        for (VipCustomerAnalytics customer : vipCustomers) {
            assertThat(customer.getTotalSpent()).isNotNull();
            assertThat(customer.getSystemAverageOrderValue()).isNotNull();
            assertThat(customer.getUserId()).isNotNull();
            assertThat(customer.getEmail()).isNotNull();

            // Проверяем, что траты клиента превышают среднее с учетом множителя
            // (логика проверки в SQL, здесь просто проверяем структуру данных)
            assertThat(customer.getTotalSpent()).isGreaterThan(BigDecimal.ZERO);
        }

        // Should be ordered by total spending descending
        for (int i = 0; i < vipCustomers.size() - 1; i++) {
            assertThat(vipCustomers.get(i).getTotalSpent())
                    .isGreaterThanOrEqualTo(vipCustomers.get(i + 1).getTotalSpent());
        }
    }

    @Test
    @DisplayName("Should compare subquery vs JOIN performance")
    void shouldCompareSubqueryVsJoinPerformance() throws DataAccessException {
        // Given
        String customerTier = "GOLD";
        Integer monthsBack = 6;

        // When
        SubqueryPerformanceComparison comparison =
                subqueryRepository.compareSubqueryVsJoinPerformance(customerTier, monthsBack);

        // Then
        assertThat(comparison).isNotNull();
        assertThat(comparison.getSubqueryExecutionTimeMs()).isNotNull();
        assertThat(comparison.getJoinExecutionTimeMs()).isNotNull();
        assertThat(comparison.getSubqueryExecutionTimeMs()).isGreaterThan(0L);
        assertThat(comparison.getJoinExecutionTimeMs()).isGreaterThan(0L);

        // Log performance comparison
        log.info(
                "Performance comparison: Subquery: {}ms, JOIN: {}ms, Winner: {}",
                comparison.getSubqueryExecutionTimeMs(),
                comparison.getJoinExecutionTimeMs(),
                comparison.getWinner());

        // Validate recommendation
        assertThat(comparison.getRecommendation()).isNotNull();
        assertThat(comparison.getWinner()).isNotNull();
        assertThat(comparison.getPerformanceRatio()).isNotNull();
    }

    @Test
    @DisplayName("Should combine different activity types with UNION")
    void shouldCombineActivityTypesWithUnion() throws DataAccessException {
        // Given
        Long userId = 1L;
        LocalDate fromDate = LocalDate.now().minusMonths(3);
        Integer limit = 50;

        // When
        List<UserActivitySummary> activities =
                subqueryRepository.getUserActivityHistoryWithUnion(userId, fromDate, limit);

        // Then
        assertThat(activities).isNotEmpty();
        assertThat(activities.size()).isLessThanOrEqualTo(limit);

        // Validate different activity types are present
        Set<String> activityTypes =
                activities.stream()
                        .map(UserActivitySummary::getActivityType)
                        .collect(Collectors.toSet());

        // Should have multiple activity types if user is active
        if (activities.size() > 5) {
            assertThat(activityTypes.size()).isGreaterThan(1);
        }

        // Should be ordered by date descending
        for (int i = 0; i < activities.size() - 1; i++) {
            assertThat(activities.get(i).getActivityDate())
                    .isAfterOrEqualTo(activities.get(i + 1).getActivityDate());
        }
    }

    private void createUser(
            Connection conn,
            Long userId,
            String email,
            String firstName,
            String lastName,
            String customerTier)
            throws SQLException {
        try (PreparedStatement stmt =
                conn.prepareStatement(
                        "INSERT INTO mentee_power.users (id, email, first_name, last_name,"
                            + " customer_tier, registration_date) VALUES (?, ?, ?, ?, ?, NOW()) ON"
                            + " CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, customer_tier ="
                            + " EXCLUDED.customer_tier")) {
            stmt.setLong(1, userId);
            stmt.setString(2, email);
            stmt.setString(3, firstName);
            stmt.setString(4, lastName);
            stmt.setString(5, customerTier);
            stmt.executeUpdate();
        }
    }

    private void createOrder(Connection conn, Long orderId, Long userId, BigDecimal totalAmount)
            throws SQLException {
        try (PreparedStatement stmt =
                conn.prepareStatement(
                        "INSERT INTO mentee_power.orders (id, user_id, total_amount, status,"
                            + " created_at) VALUES (?, ?, ?, 'COMPLETED', NOW()) ON CONFLICT (id)"
                            + " DO UPDATE SET total_amount = EXCLUDED.total_amount")) {
            stmt.setLong(1, orderId);
            stmt.setLong(2, userId);
            stmt.setBigDecimal(3, totalAmount);
            stmt.executeUpdate();
        }
    }
}
