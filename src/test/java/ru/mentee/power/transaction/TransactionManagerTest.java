/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
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
import ru.mentee.power.transaction.model.IsolationTestReport;

@Testcontainers
class TransactionManagerTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("postgres")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private DataSource dataSource;
    private TransactionManager transactionManager;

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

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection()) {
            insertTestAccount(conn);
        }
        transactionManager = new JdbcTransactionManager(dataSource);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    @DisplayName("Should prevent dirty reads at READ_COMMITTED level")
    void shouldPreventDirtyReadsAtReadCommitted() throws Exception {
        IsolationTestReport report =
                transactionManager.testIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);

        assertThat(report.getDirtyReadTest().isAnomalyPrevented()).isTrue();
        assertThat(report.getIsolationLevel()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
    }

    private void insertTestAccount(Connection connection) throws Exception {
        String sql =
                "INSERT INTO accounts (id, account_number, balance, status) VALUES (?, ?, ?, ?) ON"
                        + " CONFLICT (id) DO NOTHING";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setLong(1, 1L);
            ps.setString(2, "ACC001");
            ps.setBigDecimal(3, new BigDecimal("1000.00"));
            ps.setString(4, "ACTIVE");
            ps.executeUpdate();
        }
    }
}
