/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.jdbc.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
import ru.mentee.power.jdbc.interfaces.DatabaseSchemaAnalyzer;
import ru.mentee.power.model.mp173.SchemaInfo;
import ru.mentee.power.model.mp173.TableOptimizationInfo;
import ru.mentee.power.model.mp173.TableStatistics;

@Testcontainers
public class PostgresDatabaseSchemaAnalyzerTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private Liquibase liquibase;
    private DatabaseSchemaAnalyzer analyzer;
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
                // для тестирования DatabaseSchemaAnalyzer
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
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        analyzer = new PostgresDatabaseSchemaAnalyzer(config);
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

    @Test
    @DisplayName("Should analyze database schema through DatabaseMetaData")
    void shouldAnalyzeDatabaseSchema() throws SQLException {
        // Given
        // analyzer уже инициализирован в setUp()

        // When
        SchemaInfo schema = analyzer.analyzeDatabaseSchema();

        // Then
        assertThat(schema).isNotNull();
        assertThat(schema.getDatabaseProductName()).contains("PostgreSQL");
        assertThat(schema.getDatabaseVersion()).isNotEmpty();
        assertThat(schema.getDriverName()).contains("PostgreSQL");
        assertThat(schema.getJdbcVersion()).isNotEmpty();
        assertThat(schema.getSchemaName()).isEqualTo("mentee_power");
    }

    @Test
    @DisplayName("Should find missing indexes on foreign keys")
    void shouldFindMissingIndexes() throws SQLException {
        // Given
        // analyzer уже инициализирован в setUp()

        // When
        List<TableOptimizationInfo> optimizations = analyzer.findMissingIndexes();

        // Then
        assertThat(optimizations).isNotNull();
        // Может быть пустым, если все FK уже имеют индексы
        // Проверяем структуру объектов
        for (TableOptimizationInfo opt : optimizations) {
            assertThat(opt.getTableName()).isNotEmpty();
            assertThat(opt.getSchemaName()).isEqualTo("mentee_power");
            assertThat(opt.getForeignKeyCount()).isGreaterThan(0);
            assertThat(opt.getIndexedForeignKeyCount()).isGreaterThanOrEqualTo(0);
            assertThat(opt.getIndexedForeignKeyCount())
                    .isLessThanOrEqualTo(opt.getForeignKeyCount());
        }
    }

    @Test
    @DisplayName("Should get table statistics")
    void shouldGetTableStatistics() throws SQLException {
        // Given
        // analyzer уже инициализирован в setUp()

        // When
        Map<String, TableStatistics> statistics = analyzer.getTableStatistics();

        // Then
        assertThat(statistics).isNotNull();
        assertThat(statistics).isNotEmpty();

        // Проверяем наличие основных таблиц
        assertThat(statistics).containsKeys("users", "orders", "products");

        // Проверяем структуру статистики для каждой таблицы
        for (TableStatistics stats : statistics.values()) {
            assertThat(stats.getTableName()).isNotEmpty();
            assertThat(stats.getSchemaName()).isEqualTo("mentee_power");
            assertThat(stats.getRowCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getTableSize()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getIndexCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getSequentialScans()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getIndexScans()).isGreaterThanOrEqualTo(0);

            // Проверяем эффективность индексов (если есть сканирования)
            if (stats.getIndexEfficiency() != null) {
                assertThat(stats.getIndexEfficiency()).isBetween(0.0, 100.0);
            }
        }
    }

    @Test
    @DisplayName("Should return correct database product information")
    void shouldReturnCorrectDatabaseProductInformation() throws SQLException {
        // Given
        // analyzer уже инициализирован в setUp()

        // When
        SchemaInfo schema = analyzer.analyzeDatabaseSchema();

        // Then
        assertThat(schema.getDatabaseProductName()).isEqualTo("PostgreSQL");
        assertThat(schema.getDriverName()).contains("PostgreSQL JDBC Driver");
    }
}
