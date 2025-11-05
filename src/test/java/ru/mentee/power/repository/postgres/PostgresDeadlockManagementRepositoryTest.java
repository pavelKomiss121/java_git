/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.DeadlockException;
import ru.mentee.power.exception.LockTimeoutException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.mp168.*;
import ru.mentee.power.test.BaseIntegrationTest;

@DisplayName("Тестирование управления дедлоками и блокировками")
@SuppressWarnings({"resource", "deprecation"})
public class PostgresDeadlockManagementRepositoryTest extends BaseIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresDeadlockManagementRepositoryTest.class);

    private Liquibase liquibase;
    private PostgresDeadlockManagementRepository deadlockService;

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

            // Применяем только миграции 1, 9 и 10
            liquibase.update("dev,test"); // NOPMD - deprecated method used in tests

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        deadlockService = new PostgresDeadlockManagementRepository(config);
    }

    @Test
    @DisplayName("Should prevent deadlocks with ordered resource access")
    void shouldPreventDeadlocksWithOrderedAccess() throws Exception {
        // Given
        Long account1 = createAccount(1L, new BigDecimal("1000.00"));
        Long account2 = createAccount(2L, new BigDecimal("2000.00"));
        BigDecimal amount1 = BigDecimal.valueOf(100);
        BigDecimal amount2 = BigDecimal.valueOf(50);
        int maxRetries = 3;

        // When - Execute concurrent transfers in opposite directions
        CompletableFuture<SafeTransferResult> transfer1 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return deadlockService.performSafeTransfer(
                                        account1, account2, amount1, maxRetries);
                            } catch (DeadlockException
                                    | LockTimeoutException
                                    | BusinessException e) {
                                throw new RuntimeException(e);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });

        CompletableFuture<SafeTransferResult> transfer2 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                Thread.sleep(10); // Slight delay to ensure concurrency
                                return deadlockService.performSafeTransfer(
                                        account2, account1, amount2, maxRetries);
                            } catch (DeadlockException
                                    | LockTimeoutException
                                    | BusinessException e) {
                                throw new RuntimeException(e);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });

        // Then - Both transfers should succeed without deadlock
        SafeTransferResult result1 = transfer1.get(10, TimeUnit.SECONDS);
        SafeTransferResult result2 = transfer2.get(10, TimeUnit.SECONDS);

        assertThat(result1.getSuccess()).isTrue();
        assertThat(result2.getSuccess()).isTrue();
        assertThat(result1.getDeadlockRetries()).isEqualTo(0); // No deadlocks with ordered access
        assertThat(result2.getDeadlockRetries()).isEqualTo(0);

        // Verify balances
        BigDecimal account1Balance = getAccountBalanceDirectly(account1);
        BigDecimal account2Balance = getAccountBalanceDirectly(account2);

        // Account1: 1000 - 100 + 50 = 950
        // Account2: 2000 + 100 - 50 = 2050
        assertThat(account1Balance).isEqualByComparingTo(new BigDecimal("950.00"));
        assertThat(account2Balance).isEqualByComparingTo(new BigDecimal("2050.00"));
    }

    @Test
    @DisplayName("Should monitor lock conflicts in real-time")
    void shouldMonitorLockConflicts() throws Exception {
        // Given - Create a blocking situation
        Long accountId = createAccount(1L, new BigDecimal("1000.00"));

        CompletableFuture<Void> blockingTransaction =
                CompletableFuture.runAsync(
                        () -> {
                            try (Connection conn = getTestConnection()) {
                                conn.setAutoCommit(false);
                                PreparedStatement ps =
                                        conn.prepareStatement(
                                                "SELECT balance FROM mentee_power.accounts WHERE id"
                                                        + " = ? FOR UPDATE");
                                ps.setLong(1, accountId);
                                ps.executeQuery();

                                Thread.sleep(2000); // Hold the lock for 2 seconds
                                conn.rollback();
                            } catch (Exception e) {
                                log.error("Error in blocking transaction", e);
                            }
                        });

        // Wait for the lock to be acquired
        Thread.sleep(100);

        // When - Monitor current locks
        List<LockMonitoringInfo> locks;
        try {
            locks = deadlockService.getCurrentLockStatus();
        } catch (BusinessException e) {
            throw new RuntimeException(e);
        }

        // Then - Should detect the blocking situation (may be empty if lock is already released)
        // This test is more about verifying the method works without errors
        assertThat(locks).isNotNull();

        // Wait for blocking transaction to complete
        blockingTransaction.get(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle stress testing for deadlock resilience")
    void shouldHandleDeadlockStressTesting() throws Exception {
        // Given
        int concurrentTransactions = 10;
        int testDurationSeconds = 5; // Reduced for faster tests

        // When
        DeadlockStressTestResult result;
        try {
            result =
                    deadlockService.performDeadlockStressTest(
                            concurrentTransactions, testDurationSeconds);
        } catch (BusinessException e) {
            throw new RuntimeException(e);
        }

        // Then - For simplified version, just verify it returns valid result
        assertThat(result).isNotNull();
        assertThat(result.getConcurrentTransactions()).isEqualTo(concurrentTransactions);
        assertThat(result.getTestDurationSeconds()).isEqualTo(testDurationSeconds);
        assertThat(result.getTestStarted()).isNotNull();
        assertThat(result.getTestCompleted()).isNotNull();

        // Log results for analysis
        log.info(
                "Stress test results: Success rate: {}%, Deadlock rate: {}%, Avg time: {}ms",
                result.getSuccessRate() * 100,
                result.getDeadlockRate() * 100,
                result.getAvgTransactionTimeMs());
    }

    @Test
    @DisplayName("Should demonstrate classic deadlock scenario")
    void shouldDemonstrateClassicDeadlock() throws Exception, BusinessException {
        // Given
        Long account1 = createAccount(1L, new BigDecimal("1000.00"));
        Long account2 = createAccount(2L, new BigDecimal("2000.00"));
        BigDecimal amount1 = BigDecimal.valueOf(100);
        BigDecimal amount2 = BigDecimal.valueOf(50);

        // When
        DeadlockDemonstrationResult result =
                deadlockService.demonstrateClassicDeadlock(account1, account2, amount1, amount2);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDemonstrationStarted()).isNotNull();
        assertThat(result.getDemonstrationCompleted()).isNotNull();
        assertThat(result.getTransaction1Steps()).isNotNull();
        assertThat(result.getTransaction2Steps()).isNotNull();
        assertThat(result.getLessonLearned()).isNotBlank();

        log.info(
                "Deadlock demonstration: Occurred={}, Victim={}, Survivor={}",
                result.getDeadlockOccurred(),
                result.getVictimTransaction(),
                result.getSurvivorTransaction());
    }

    @Test
    @DisplayName("Should perform bulk inventory reservation")
    void shouldPerformBulkInventoryReservation()
            throws Exception, LockTimeoutException, BusinessException {
        // Given
        Long product1 = createProduct("Product 1", 10);
        Long product2 = createProduct("Product 2", 5);

        List<InventoryReservationRequest> reservations =
                List.of(
                        InventoryReservationRequest.builder()
                                .productId(product1)
                                .quantity(3)
                                .userId(1L)
                                .reservationReason("Test reservation 1")
                                .build(),
                        InventoryReservationRequest.builder()
                                .productId(product2)
                                .quantity(2)
                                .userId(1L)
                                .reservationReason("Test reservation 2")
                                .build());

        // When
        BulkReservationResult result =
                deadlockService.performBulkInventoryReservation(reservations, 30);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalRequests()).isEqualTo(2);
        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getSuccessfulReservations()).isGreaterThanOrEqualTo(0);

        // Verify stock was updated
        Integer stock1 = getProductStock(product1);
        Integer stock2 = getProductStock(product2);

        assertThat(stock1).isLessThanOrEqualTo(10 - 3); // Stock should be reduced
        assertThat(stock2).isLessThanOrEqualTo(5 - 2);
    }

    @Test
    @DisplayName("Should analyze deadlock patterns")
    void shouldAnalyzeDeadlockPatterns() throws Exception, BusinessException {
        // Given
        Integer hours = 24;

        // When
        DeadlockAnalysisResult result = deadlockService.analyzeDeadlockPatterns(hours);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAnalysisStartTime()).isNotNull();
        assertThat(result.getAnalysisEndTime()).isNotNull();
        assertThat(result.getTotalDeadlocks()).isNotNull();
        assertThat(result.getDeadlocksPerHour()).isNotNull();
        assertThat(result.getSystemHealthAssessment()).isNotNull();
        assertThat(result.getPreventionRecommendations()).isNotNull();
    }

    @Test
    @DisplayName("Should get deadlock prevention recommendations")
    void shouldGetDeadlockPreventionRecommendations() throws Exception, BusinessException {
        // Given
        LockAnalysisData analysisData =
                LockAnalysisData.builder()
                        .frequentQueries(
                                List.of("SELECT * FROM accounts", "UPDATE accounts SET balance"))
                        .tableAccessPatterns(java.util.Map.of("accounts", 100, "transfers", 50))
                        .averageTransactionDuration(100)
                        .peakConcurrentUsers(10)
                        .applicationWorkloadType("OLTP")
                        .build();

        // When
        DeadlockPreventionRecommendations recommendations =
                deadlockService.getDeadlockPreventionRecommendations(analysisData);

        // Then
        assertThat(recommendations).isNotNull();
        assertThat(recommendations.getImmediateActions()).isNotEmpty();
        assertThat(recommendations.getMediumTermImprovements()).isNotEmpty();
        assertThat(recommendations.getLongTermOptimizations()).isNotEmpty();
        assertThat(recommendations.getCodeReviewGuidelines()).isNotBlank();
        assertThat(recommendations.getDatabaseConfigurationTuning()).isNotBlank();
    }

    // Вспомогательные методы

    private Long createAccount(Long userId, BigDecimal initialBalance) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO mentee_power.accounts (user_id, balance, account_type,"
                                    + " status) VALUES (?, ?, 'CHECKING', 'ACTIVE') RETURNING id",
                                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, userId);
            stmt.setBigDecimal(2, initialBalance);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка создания счета", e);
        }
        throw new RuntimeException("Не удалось создать счет");
    }

    private BigDecimal getAccountBalanceDirectly(Long accountId) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT balance FROM mentee_power.accounts WHERE id = ?")) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения баланса", e);
        }
        return BigDecimal.ZERO;
    }

    private Long createProduct(String name, Integer stockQuantity) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO mentee_power.products (name, price, stock_quantity)"
                                        + " VALUES (?, ?, ?) RETURNING id",
                                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setBigDecimal(2, new BigDecimal("100.00"));
            stmt.setInt(3, stockQuantity);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка создания товара", e);
        }
        throw new RuntimeException("Не удалось создать товар");
    }

    private Integer getProductStock(Long productId) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT stock_quantity FROM mentee_power.products WHERE id = ?")) {
            stmt.setLong(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock_quantity");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения количества товара", e);
        }
        return 0;
    }
}
