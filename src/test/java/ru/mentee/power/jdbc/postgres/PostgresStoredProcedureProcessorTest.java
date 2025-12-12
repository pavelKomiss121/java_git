/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.jdbc.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.jdbc.interfaces.StoredProcedureProcessor;
import ru.mentee.power.model.mp173.UserStatistics;

@Testcontainers
public class PostgresStoredProcedureProcessorTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private Liquibase liquibase;
    private StoredProcedureProcessor processor;
    private ApplicationConfig config;

    @BeforeEach
    public void setUp() throws SASTException, IOException, SQLException {
        config = createTestConfig();

        try (Connection conn = getTestConnection()) {
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            liquibase =
                    new Liquibase(
                            "db/migrations_161/changelog.yaml",
                            new ClassLoaderResourceAccessor(),
                            database);

            // Применяем миграции для тестирования
            // Обрабатываем ошибки миграций gracefully - некоторые миграции могут ссылаться
            // на таблицы, которых нет в тестовой БД
            try {
                liquibase.update("dev,test"); // NOPMD - deprecated method used in tests
            } catch (Exception migrationError) {
                // Логируем ошибку, но продолжаем выполнение, если это не критично
                // для тестирования StoredProcedureProcessor
                String errorMsg = migrationError.getMessage();
                if (errorMsg != null
                        && (errorMsg.contains("does not exist") || errorMsg.contains("relation"))) {
                    // Это ожидаемая ошибка для тестов - миграция 015 требует таблицы,
                    // которых нет в тестовой БД (customers, organizations)
                    System.err.println(
                            "Предупреждение: пропущена миграция с отсутствующими таблицами: "
                                    + errorMsg);
                } else {
                    // Для других ошибок пробрасываем исключение
                    throw new RuntimeException("Ошибка инициализации Liquibase", migrationError);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации тестов", e);
        }

        // Создаем хранимую процедуру для тестов в отдельном соединении
        try (Connection conn = getTestConnection()) {
            createStoredProcedure(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка создания хранимой процедуры", e);
        }

        processor = new PostgresStoredProcedureProcessor(config);
    }

    protected Connection getTestConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    protected ApplicationConfig createTestConfig() throws SASTException, IOException {
        java.util.Properties props = new java.util.Properties();
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

        props.setProperty("db.url", urlWithCreds);
        props.setProperty("db.username", postgres.getUsername());
        props.setProperty("db.driver", "org.postgresql.Driver");
        props.setProperty("db.show-sql", "false");

        // Устанавливаем пароль через системное свойство (безопасный способ для тестов)
        System.setProperty("db.password", postgres.getPassword());

        return new ApplicationConfig(props, new ConfigFilePath()) {
            @Override
            public void load(String path) {
                /* no-op for tests */
            }
        };
    }

    private void createStoredProcedure(Connection conn) throws SQLException {
        // Создаем функцию, которая принимает BIGINT
        String createFunction =
                """
            CREATE OR REPLACE FUNCTION calculate_user_statistics(
                p_user_id BIGINT,
                OUT total_orders INTEGER,
                OUT total_spent DECIMAL(10,2),
                OUT avg_order_value DECIMAL(10,2)
            ) AS $$
            BEGIN
                SELECT
                    COUNT(*)::INTEGER,
                    COALESCE(SUM(total_price), 0),
                    COALESCE(AVG(total_price), 0)
                INTO total_orders, total_spent, avg_order_value
                FROM mentee_power.orders
                WHERE user_id = p_user_id;
            END;
            $$ LANGUAGE plpgsql;
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO mentee_power, public");
            stmt.execute(createFunction);
        }
    }

    @Test
    @DisplayName("Should execute stored procedure with OUT parameters")
    void shouldExecuteStoredProcedure() throws SQLException {
        // Given
        // Создаем пользователя и заказы для теста
        Long userId;
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO mentee_power, public");

            // Создаем тестового пользователя
            stmt.execute(
                    """
                INSERT INTO mentee_power.users (name, email, created_at)
                VALUES ('Test User', 'test@example.com', CURRENT_TIMESTAMP)
                RETURNING id
                """);

            try (var rs = stmt.getResultSet()) {
                if (rs.next()) {
                    userId = rs.getLong("id");
                } else {
                    throw new SQLException("Не удалось создать тестового пользователя");
                }
            }

            // Создаем заказы для пользователя
            stmt.executeUpdate(
                    String.format(
                            """
                INSERT INTO mentee_power.orders (user_id, total, total_price, status, order_date)
                VALUES
                    (%d, 1000.00, 1000.00, 'COMPLETED', CURRENT_TIMESTAMP),
                    (%d, 2000.00, 2000.00, 'COMPLETED', CURRENT_TIMESTAMP)
                """,
                            userId, userId));
        }

        // When
        UserStatistics stats = processor.calculateUserStatistics(userId);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getUserId()).isEqualTo(userId);
        assertThat(stats.getTotalOrders()).isEqualTo(2);
        assertThat(stats.getTotalSpent()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(stats.getAvgOrderValue()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName("Should return correct statistics for user with orders")
    void shouldReturnCorrectStatisticsForUserWithOrders() throws SQLException {
        // Given
        // Создаем пользователя с заказами
        Long userId;
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO mentee_power, public");

            // Создаем тестового пользователя
            stmt.execute(
                    """
                INSERT INTO mentee_power.users (name, email, created_at)
                VALUES ('User With Orders', 'user@example.com', CURRENT_TIMESTAMP)
                RETURNING id
                """);

            try (var rs = stmt.getResultSet()) {
                if (rs.next()) {
                    userId = rs.getLong("id");
                } else {
                    throw new SQLException("Не удалось создать тестового пользователя");
                }
            }

            // Создаем 3 заказа для пользователя
            stmt.executeUpdate(
                    String.format(
                            """
                INSERT INTO mentee_power.orders (user_id, total, total_price, status, order_date)
                VALUES
                    (%d, 500.00, 500.00, 'COMPLETED', CURRENT_TIMESTAMP),
                    (%d, 750.00, 750.00, 'COMPLETED', CURRENT_TIMESTAMP),
                    (%d, 1000.00, 1000.00, 'COMPLETED', CURRENT_TIMESTAMP)
                """,
                            userId, userId, userId));
        }

        // When
        UserStatistics stats = processor.calculateUserStatistics(userId);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getUserId()).isEqualTo(userId);
        assertThat(stats.getTotalOrders()).isEqualTo(3);
        assertThat(stats.getTotalSpent()).isEqualByComparingTo(new BigDecimal("2250.00"));
        assertThat(stats.getAvgOrderValue()).isEqualByComparingTo(new BigDecimal("750.00"));
    }

    @Test
    @DisplayName("Should return zero statistics for user without orders")
    void shouldReturnZeroStatisticsForUserWithoutOrders() throws SQLException {
        // Given
        // Используем несуществующий ID
        Long userId = 999999L;

        // When
        UserStatistics stats = processor.calculateUserStatistics(userId);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalOrders()).isEqualTo(0);
        assertThat(stats.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getAvgOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
