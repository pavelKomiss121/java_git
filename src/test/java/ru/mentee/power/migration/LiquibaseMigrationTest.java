/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.migration;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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

@DisplayName("Тестирование Liquibase SQL миграций")
class LiquibaseMigrationTest extends BaseIntegrationTest {

    private Liquibase liquibase;

    @BeforeEach
    @Override
    protected void setUp() throws SASTException, IOException, DataAccessException {
        super.setUp();

        try (Connection conn = getTestConnection()) {
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            liquibase =
                    new Liquibase("db/changelog.yaml", new ClassLoaderResourceAccessor(), database);

            // Применяем миграции для тестирования
            liquibase.update("dev,test");

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }
    }

    @Test
    @DisplayName("Should применить все SQL миграции через YAML changelog без ошибок")
    void shouldApplyAllSqlMigrationsThroughYamlChangelogSuccessfully() throws Exception {
        // Given - миграции уже применены в setUp()

        // When - проверяем что таблицы созданы
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {

            stmt.execute("SET search_path TO mentee_power");

            // Then - проверяем что все таблицы созданы
            ResultSet rs =
                    stmt.executeQuery(
                            "SELECT table_name FROM information_schema.tables WHERE table_schema ="
                                    + " 'mentee_power' AND table_type = 'BASE TABLE'");

            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }

            assertThat(tables).contains("users", "orders", "products");

            try (ResultSet rs2 =
                    stmt.executeQuery(
                            "select to_regclass('public.databasechangelog') is not null as ok1,    "
                                + "    to_regclass('public.databasechangeloglock') is not null as"
                                + " ok2")) {
                rs2.next();
                assertThat(rs2.getBoolean("ok1")).isTrue();
                assertThat(rs2.getBoolean("ok2")).isTrue();
            }
        }
    }

    @Test
    @DisplayName("Should создать все обязательные таблицы из SQL миграций")
    void shouldCreateAllRequiredTablesFromSqlMigrations() throws DataAccessException {
        MigrationRepository repo = new MigrationRepository();
        assertThat(repo.validateSchemaStructure(getPostgresContainer())).isTrue();
    }

    @Test
    @DisplayName("Should создать все foreign key constraints из SQL миграций")
    void shouldCreateAllForeignKeyConstraintsFromSqlMigrations() throws DataAccessException {
        MigrationRepository repo = new MigrationRepository();
        assertThat(repo.validateForeignKeyConstraints(getPostgresContainer())).isTrue();
    }

    @Test
    @DisplayName("Should создать все индексы для производительности из SQL миграций")
    void shouldCreateAllPerformanceIndexesFromSqlMigrations() throws DataAccessException {
        MigrationRepository repo = new MigrationRepository();
        assertThat(repo.validateIndexes(getPostgresContainer())).isTrue();
    }

    @Test
    @DisplayName("Should загрузить тестовые данные в dev контексте из SQL")
    void shouldLoadTestDataInDevContextFromSql() throws DataAccessException {
        MigrationRepository repo = new MigrationRepository();
        assertThat(repo.validateTestData(getPostgresContainer())).isTrue();
    }

    @Test
    @DisplayName("Should отследить все SQL изменения в databasechangelog")
    void shouldTrackAllSqlChangesInChangelog() {
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            ResultSet rs =
                    stmt.executeQuery(
                            "select id, author from public.databasechangelog order by"
                                    + " dateexecuted");
            List<String> ids = new ArrayList<>();
            List<String> authors = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString(1));
                authors.add(rs.getString(2));
            }

            assertThat(ids)
                    .contains(
                            "001-create-schema",
                            "002-create-users-table",
                            "003-create-orders-table",
                            "005-create-products-table",
                            "004-add-user-phone",
                            "004-fix-phone-regex",
                            "dev-001-insert-test-users",
                            "dev-002-insert-test-products");
            assertThat(authors).isNotEmpty();
        } catch (Exception e) {
            fail("Changelog tracking check failed", e);
        }
    }

    @Test
    @DisplayName("Should корректно обработать повторное применение SQL миграций")
    void shouldHandleReapplyingSqlMigrations() {
        try (Connection conn = getTestConnection()) {
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase lb =
                    new Liquibase("db/changelog.yaml", new ClassLoaderResourceAccessor(), database);

            try (Statement s = conn.createStatement();
                    ResultSet before =
                            s.executeQuery("select count(*) from public.databasechangelog")) {
                before.next();
                int countBefore = before.getInt(1);
                lb.update("dev,test");
                try (ResultSet after =
                        s.executeQuery("select count(*) from public.databasechangelog")) {
                    after.next();
                    int countAfter = after.getInt(1);
                    assertThat(countAfter).isEqualTo(countBefore);
                }
            }
        } catch (Exception e) {
            fail("Reapply check failed", e);
        }
    }

    @Test
    @DisplayName("Should валидировать целостность данных после SQL миграций")
    void shouldValidateDataIntegrityAfterSqlMigrations() {
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO mentee_power");

            // unique email
            assertThatThrownBy(
                            () ->
                                    stmt.executeUpdate(
                                            "INSERT INTO users(name,email) VALUES"
                                                    + " ('Dup','alex@menteepower.com')"))
                    .isInstanceOf(Exception.class);

            // check phone
            assertThatThrownBy(
                            () ->
                                    stmt.executeUpdate(
                                            "INSERT INTO users(name,email,phone) VALUES"
                                                    + " ('Bad','bad@x.com','123-456')"))
                    .isInstanceOf(Exception.class);

            // fk orders.user_id must reference users.id (use non-existing id)
            assertThatThrownBy(
                            () ->
                                    stmt.executeUpdate(
                                            "INSERT INTO orders(user_id,total_price,status) VALUES"
                                                    + " (999999,'10.00','PENDING')"))
                    .isInstanceOf(Exception.class);
        } catch (Exception e) {
            fail("Data integrity check failed", e);
        }
    }

    @Test
    @DisplayName("Should проверить корректность YAML changelog структуры")
    void shouldValidateYamlChangelogStructure() {
        // Простейшая валидация: файл существует и содержит include нужных SQL
        String yamlPath = "src/main/resources/db/changelog.yaml";
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(yamlPath);
            String content = java.nio.file.Files.readString(p);
            assertThat(content)
                    .contains("migrations/001-create-schema.sql")
                    .contains("migrations/002-create-users-table.sql")
                    .contains("migrations/003-create-orders-table.sql")
                    .contains("migrations/005-create-products-table.sql")
                    .contains("migrations/004-add-user-phone.sql");
        } catch (IOException e) {
            fail("Не удалось прочитать changelog.yaml", e);
        }
    }

    @Test
    @DisplayName("Should проверить корректность SQL Liquibase форматирования")
    void shouldValidateSqlLiquibaseFormatting() {
        String[] sqlFiles =
                new String[] {
                    "src/main/resources/db/migrations/001-create-schema.sql",
                    "src/main/resources/db/migrations/002-create-users-table.sql",
                    "src/main/resources/db/migrations/003-create-orders-table.sql",
                    "src/main/resources/db/migrations/004-add-user-phone.sql",
                    "src/main/resources/db/migrations/005-create-products-table.sql",
                    "src/main/resources/db/testdata/dev-test-data.sql"
                };
        for (String path : sqlFiles) {
            try {
                String content = java.nio.file.Files.readString(java.nio.file.Paths.get(path));
                assertThat(content).contains("--liquibase formatted sql");
                assertThat(content).contains("--changeset ");
                assertThat(content.toLowerCase()).contains("--rollback");
            } catch (IOException e) {
                fail("Не удалось прочитать " + path, e);
            }
        }
    }
}

@DisplayName("Тестирование отката SQL Liquibase миграций")
class LiquibaseSqlRollbackTest extends BaseIntegrationTest {

    private Liquibase liquibase;

    @BeforeEach
    @Override
    protected void setUp() throws SASTException, IOException, DataAccessException {
        super.setUp();
        try (Connection conn = getTestConnection()) {
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            liquibase =
                    new Liquibase("db/changelog.yaml", new ClassLoaderResourceAccessor(), database);
            liquibase.update("dev,test");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should корректно откатить последнюю SQL миграцию")
    void shouldRollbackLastSqlMigrationCorrectly() throws Exception {
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            // before count - Liquibase tables are in public schema
            ResultSet rs = stmt.executeQuery("select count(*) from public.databasechangelog");
            rs.next();
            int before = rs.getInt(1);

            // Создаем новый Liquibase с текущим соединением
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase liquibaseInstance =
                    new Liquibase("db/changelog.yaml", new ClassLoaderResourceAccessor(), database);
            liquibaseInstance.rollback(1, "dev,test");

            ResultSet rs2 = stmt.executeQuery("select count(*) from public.databasechangelog");
            rs2.next();
            int after = rs2.getInt(1);
            assertThat(after).isEqualTo(before - 1);
        }
    }

    @Test
    @DisplayName("Should откатить несколько SQL миграций подряд")
    void shouldRollbackMultipleSqlMigrations() throws Exception {
        try (Connection conn = getTestConnection();
                Statement stmt = conn.createStatement()) {
            // Liquibase tables are in public schema
            ResultSet rs = stmt.executeQuery("select count(*) from public.databasechangelog");
            rs.next();
            int before = rs.getInt(1);

            // Создаем новый Liquibase с текущим соединением
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase liquibaseInstance =
                    new Liquibase("db/changelog.yaml", new ClassLoaderResourceAccessor(), database);
            liquibaseInstance.rollback(2, "dev,test");

            ResultSet rs2 = stmt.executeQuery("select count(*) from public.databasechangelog");
            rs2.next();
            int after = rs2.getInt(1);
            assertThat(after).isEqualTo(before - 2);

            // вернём обратно, чтобы не мешать другим тестам
            liquibaseInstance.update("dev,test");
        }
    }
}
