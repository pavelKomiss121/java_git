/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.test.BaseIntegrationTest;

/**
 * Интеграционные тесты для проверки работы уровней изоляции транзакций.
 *
 * Тесты проверяют корректность работы метода executeWithIsolationLevel
 * с различными уровнями изоляции:
 * - READ UNCOMMITTED
 * - READ COMMITTED
 * - REPEATABLE READ
 * - SERIALIZABLE
 */
@DisplayName("Тестирование уровней изоляции транзакций")
@SuppressWarnings({"resource", "deprecation"})
public class PostgresIsolationLevelRepositoryTest extends BaseIntegrationTest {

    private Liquibase liquibase;
    private PostgresIsolationLevelRepository repository;

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

            liquibase.update("dev,test");

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        repository = new PostgresIsolationLevelRepository(config);
    }

    @Test
    @DisplayName("Должен выполнять операцию с уровнем изоляции READ UNCOMMITTED")
    void shouldExecuteWithReadUncommitted() throws DataAccessException {
        // Given
        Long accountId = createTestAccount(1L, new BigDecimal("1000.00"));

        // When
        BigDecimal result =
                repository.executeWithIsolationLevel(
                        "READ UNCOMMITTED",
                        (Connection conn) -> {
                            String sql = "SELECT balance FROM mentee_power.accounts WHERE id = ?";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setLong(1, accountId);
                                try (ResultSet rs = stmt.executeQuery()) {
                                    if (rs.next()) {
                                        return rs.getBigDecimal("balance");
                                    }
                                }
                            }
                            return BigDecimal.ZERO;
                        });

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Должен выполнять операцию с уровнем изоляции READ COMMITTED")
    void shouldExecuteWithReadCommitted() throws DataAccessException {
        // Given
        Long accountId = createTestAccount(1L, new BigDecimal("2000.00"));

        // When
        BigDecimal result =
                repository.executeWithIsolationLevel(
                        "READ COMMITTED",
                        (Connection conn) -> {
                            String sql = "SELECT balance FROM mentee_power.accounts WHERE id = ?";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setLong(1, accountId);
                                try (ResultSet rs = stmt.executeQuery()) {
                                    if (rs.next()) {
                                        return rs.getBigDecimal("balance");
                                    }
                                }
                            }
                            return BigDecimal.ZERO;
                        });

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("Должен выполнять операцию с уровнем изоляции REPEATABLE READ")
    void shouldExecuteWithRepeatableRead() throws DataAccessException {
        // Given
        Long accountId = createTestAccount(1L, new BigDecimal("3000.00"));

        // When
        BigDecimal result =
                repository.executeWithIsolationLevel(
                        "REPEATABLE READ",
                        (Connection conn) -> {
                            String sql = "SELECT balance FROM mentee_power.accounts WHERE id = ?";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setLong(1, accountId);
                                try (ResultSet rs = stmt.executeQuery()) {
                                    if (rs.next()) {
                                        return rs.getBigDecimal("balance");
                                    }
                                }
                            }
                            return BigDecimal.ZERO;
                        });

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    @Test
    @DisplayName("Должен выполнять операцию с уровнем изоляции SERIALIZABLE")
    void shouldExecuteWithSerializable() throws DataAccessException {
        // Given
        Long accountId = createTestAccount(1L, new BigDecimal("4000.00"));

        // When
        BigDecimal result =
                repository.executeWithIsolationLevel(
                        "SERIALIZABLE",
                        (Connection conn) -> {
                            String sql = "SELECT balance FROM mentee_power.accounts WHERE id = ?";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setLong(1, accountId);
                                try (ResultSet rs = stmt.executeQuery()) {
                                    if (rs.next()) {
                                        return rs.getBigDecimal("balance");
                                    }
                                }
                            }
                            return BigDecimal.ZERO;
                        });

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("4000.00"));
    }

    @Test
    @DisplayName("Должен обновлять баланс с уровнем изоляции READ COMMITTED")
    void shouldUpdateBalanceWithReadCommitted() throws DataAccessException {
        // Given
        Long accountId = createTestAccount(1L, new BigDecimal("1000.00"));

        // When
        Integer rowsAffected =
                repository.executeWithIsolationLevel(
                        "READ COMMITTED",
                        (Connection conn) -> {
                            String sql =
                                    "UPDATE mentee_power.accounts SET balance = balance + ? WHERE"
                                            + " id = ?";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setBigDecimal(1, new BigDecimal("500.00"));
                                stmt.setLong(2, accountId);
                                return stmt.executeUpdate();
                            }
                        });

        // Then
        assertThat(rowsAffected).isEqualTo(1);
        BigDecimal newBalance = getAccountBalance(accountId);
        assertThat(newBalance).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName("Должен откатывать транзакцию при ошибке")
    void shouldRollbackOnError() throws DataAccessException {
        // Given
        Long accountId = createTestAccount(1L, new BigDecimal("1000.00"));
        BigDecimal initialBalance = getAccountBalance(accountId);

        // When & Then
        assertThatThrownBy(
                        () -> {
                            repository.executeWithIsolationLevel(
                                    "READ COMMITTED",
                                    (Connection conn) -> {
                                        // Выполняем обновление
                                        String updateSql =
                                                "UPDATE mentee_power.accounts SET balance = balance"
                                                        + " + ? WHERE id = ?";
                                        try (PreparedStatement stmt =
                                                conn.prepareStatement(updateSql)) {
                                            stmt.setBigDecimal(1, new BigDecimal("500.00"));
                                            stmt.setLong(2, accountId);
                                            stmt.executeUpdate();
                                        }

                                        // Вызываем ошибку
                                        throw new SQLException(
                                                "Тестовая ошибка для отката транзакции");
                                    });
                        })
                .isInstanceOf(DataAccessException.class);

        // Проверяем, что баланс не изменился
        BigDecimal finalBalance = getAccountBalance(accountId);
        assertThat(finalBalance).isEqualByComparingTo(initialBalance);
    }

    @Test
    @DisplayName("Должен использовать READ COMMITTED для неизвестного уровня изоляции")
    void shouldUseReadCommittedForUnknownIsolationLevel() throws DataAccessException {
        // Given
        Long accountId = createTestAccount(1L, new BigDecimal("1000.00"));

        // When
        BigDecimal result =
                repository.executeWithIsolationLevel(
                        "UNKNOWN_LEVEL",
                        (Connection conn) -> {
                            String sql = "SELECT balance FROM mentee_power.accounts WHERE id = ?";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setLong(1, accountId);
                                try (ResultSet rs = stmt.executeQuery()) {
                                    if (rs.next()) {
                                        return rs.getBigDecimal("balance");
                                    }
                                }
                            }
                            return BigDecimal.ZERO;
                        });

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // Вспомогательные методы

    private Long createTestAccount(Long userId, BigDecimal initialBalance) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO mentee_power.accounts (user_id, balance)"
                                        + " VALUES (?, ?) RETURNING id",
                                java.sql.Statement.RETURN_GENERATED_KEYS)) {
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

    private BigDecimal getAccountBalance(Long accountId) {
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
}
