/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.mentee.power.transaction.impl.JdbcTransactionManager;
import ru.mentee.power.transaction.interfaces.TransactionManager;
import ru.mentee.power.transaction.model.RetryPolicy;
import ru.mentee.power.transaction.model.TransferResult;

@Testcontainers
class DeadlockAwareTransactionManagerTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("postgres")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private DataSource dataSource1;
    private DataSource dataSource2;
    private TransactionManager manager1;
    private TransactionManager manager2;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection liquibaseConn =
                DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            JdbcConnection jdbcConnection = new JdbcConnection(liquibaseConn);
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
            try (Liquibase liquibase =
                    new Liquibase(
                            "db/migrations_161/changelog.yaml",
                            new ClassLoaderResourceAccessor(),
                            database)) {
                liquibase.update("");
            }
        }

        HikariConfig config1 = new HikariConfig();
        config1.setJdbcUrl(postgres.getJdbcUrl());
        config1.setUsername(postgres.getUsername());
        config1.setPassword(postgres.getPassword());
        config1.setMaximumPoolSize(5);
        dataSource1 = new HikariDataSource(config1);

        HikariConfig config2 = new HikariConfig();
        config2.setJdbcUrl(postgres.getJdbcUrl());
        config2.setUsername(postgres.getUsername());
        config2.setPassword(postgres.getPassword());
        config2.setMaximumPoolSize(5);
        dataSource2 = new HikariDataSource(config2);

        try (Connection conn = dataSource1.getConnection()) {
            insertTestAccounts(conn);
        }

        manager1 = new JdbcTransactionManager(dataSource1);
        manager2 = new JdbcTransactionManager(dataSource2);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource1 instanceof HikariDataSource) {
            ((HikariDataSource) dataSource1).close();
        }
        if (dataSource2 instanceof HikariDataSource) {
            ((HikariDataSource) dataSource2).close();
        }
    }

    @Test
    @DisplayName("Should recover from deadlock with retry")
    void shouldRecoverFromDeadlockWithRetry() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean deadlockOccurred = new AtomicBoolean(false);

        CompletableFuture<TransferResult> future1 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return manager1.executeWithRetry(
                                        conn -> transferMoney(conn, 1L, 2L, new BigDecimal("100")),
                                        RetryPolicy.exponentialBackoff(3, 100));
                            } catch (SQLException e) {
                                if (e.getMessage() != null && e.getMessage().contains("deadlock")) {
                                    deadlockOccurred.set(true);
                                }
                                throw new RuntimeException(e);
                            } finally {
                                latch.countDown();
                            }
                        });

        CompletableFuture<TransferResult> future2 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                Thread.sleep(50);
                                return manager2.executeWithRetry(
                                        conn -> transferMoney(conn, 2L, 1L, new BigDecimal("50")),
                                        RetryPolicy.exponentialBackoff(3, 100));
                            } catch (SQLException e) {
                                if (e.getMessage() != null && e.getMessage().contains("deadlock")) {
                                    deadlockOccurred.set(true);
                                }
                                throw new RuntimeException(e);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } finally {
                                latch.countDown();
                            }
                        });

        latch.await(10, TimeUnit.SECONDS);

        TransferResult result1 = future1.get();
        TransferResult result2 = future2.get();

        assertThat(result1.isSuccessful() || result2.isSuccessful()).isTrue();
    }

    private TransferResult transferMoney(Connection conn, Long fromId, Long toId, BigDecimal amount)
            throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            Long firstLock = Math.min(fromId, toId);
            Long secondLock = Math.max(fromId, toId);

            lockAccount(conn, firstLock);
            lockAccount(conn, secondLock);

            BigDecimal fromBalance = getAccountBalance(conn, fromId);
            if (fromBalance.compareTo(amount) < 0) {
                throw new SQLException("Недостаточно средств");
            }

            updateAccountBalance(conn, fromId, amount.negate());
            updateAccountBalance(conn, toId, amount);

            conn.commit();
            return TransferResult.builder()
                    .successful(true)
                    .transactionId(System.currentTimeMillis())
                    .amount(amount)
                    .build();
        } catch (Exception e) {
            conn.rollback();
            return TransferResult.builder().successful(false).errorMessage(e.getMessage()).build();
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private void lockAccount(Connection conn, Long accountId) throws SQLException {
        String sql = "SELECT id FROM accounts WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.executeQuery();
        }
    }

    private BigDecimal getAccountBalance(Connection conn, Long accountId) throws SQLException {
        String sql = "SELECT balance FROM accounts WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
                return BigDecimal.ZERO;
            }
        }
    }

    private void updateAccountBalance(Connection conn, Long accountId, BigDecimal delta)
            throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, delta);
            ps.setLong(2, accountId);
            ps.executeUpdate();
        }
    }

    private void insertTestAccounts(Connection connection) throws Exception {
        String sql =
                "INSERT INTO accounts (id, account_number, balance, status) VALUES (?, ?, ?, ?) ON"
                        + " CONFLICT (id) DO NOTHING";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setLong(1, 1L);
            ps.setString(2, "ACC001");
            ps.setBigDecimal(3, new BigDecimal("1000.00"));
            ps.setString(4, "ACTIVE");
            ps.executeUpdate();

            ps.setLong(1, 2L);
            ps.setString(2, "ACC002");
            ps.setBigDecimal(3, new BigDecimal("1000.00"));
            ps.setString(4, "ACTIVE");
            ps.executeUpdate();
        }
    }
}
