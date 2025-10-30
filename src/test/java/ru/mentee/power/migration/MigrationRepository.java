/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.migration;

import java.sql.*;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.mentee.power.exception.DataAccessException;

public class MigrationRepository implements MigrationValidator {

    protected Connection getTestConnection(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    static final String SQL_VALIDATE_TABLES =
            """
      SELECT
        ( to_regclass('mentee_power.users')      IS NOT NULL
          AND to_regclass('mentee_power.orders') IS NOT NULL
          AND to_regclass('mentee_power.products') IS NOT NULL
        ) AS all_ok;
      """;

    static final String SQL_VALIDATE_KEYS =
            """
      SELECT
        ( EXISTS (
            SELECT 1 FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'mentee_power'
              AND t.relname = 'orders'
              AND c.contype = 'f'
              AND c.conname = 'orders_user_id_fkey'
          )
        ) AS all_fks_ok;
      """;

    static final String SQL_VALIDATE_INDEXES =
            """
      SELECT
        ( to_regclass('mentee_power.idx_users_email')            IS NOT NULL
          AND to_regclass('mentee_power.idx_users_created_at')  IS NOT NULL

          AND to_regclass('mentee_power.idx_orders_user_id') IS NOT NULL
          AND to_regclass('mentee_power.idx_orders_status')  IS NOT NULL
          AND to_regclass('mentee_power.idx_orders_order_date') IS NOT NULL

          AND to_regclass('mentee_power.idx_users_phone')  IS NOT NULL

          AND to_regclass('mentee_power.idx_products_category') IS NOT NULL
          AND to_regclass('mentee_power.idx_products_in_stock')  IS NOT NULL
          AND to_regclass('mentee_power.idx_products_name') IS NOT NULL
        ) AS all_ind_ok;
      """;

    static final String SQL_VALIDATE_DATA =
            """
      SELECT
        (SELECT COUNT(*) as user FROM mentee_power.users
         WHERE email IN ('alex@menteepower.com','maria@menteepower.com','ivan@menteepower.com'))
      +
        (SELECT COUNT(*) as prod FROM mentee_power.products
         WHERE name IN ('Ноутбук ASUS','iPhone 15','Клавиатура механическая')) AS total_rows;
      """;

    @Override
    public boolean validateSchemaStructure(PostgreSQLContainer postgres)
            throws DataAccessException {
        try (Connection conn = getTestConnection(postgres);
                PreparedStatement stmt = conn.prepareStatement(SQL_VALIDATE_TABLES);
                ResultSet rs = stmt.executeQuery(); ) {
            return rs.next() && rs.getBoolean("all_ok");
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка валидации таблиц", e);
        }
    }

    @Override
    public boolean validateForeignKeyConstraints(PostgreSQLContainer postgres)
            throws DataAccessException {
        try (Connection conn = getTestConnection(postgres);
                PreparedStatement stmt = conn.prepareStatement(SQL_VALIDATE_KEYS);
                ResultSet rs = stmt.executeQuery(); ) {
            return rs.next() && rs.getBoolean("all_fks_ok");
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка валидации внешних ключей", e);
        }
    }

    @Override
    public boolean validateIndexes(PostgreSQLContainer postgres) throws DataAccessException {
        try (Connection conn = getTestConnection(postgres);
                PreparedStatement stmt = conn.prepareStatement(SQL_VALIDATE_INDEXES);
                ResultSet rs = stmt.executeQuery(); ) {
            return rs.next() && rs.getBoolean("all_ind_ok");
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка валидации индексов", e);
        }
    }

    @Override
    public boolean validateTestData(PostgreSQLContainer postgres) throws DataAccessException {
        try (Connection conn = getTestConnection(postgres);
                PreparedStatement stmt = conn.prepareStatement(SQL_VALIDATE_DATA);
                ResultSet rs = stmt.executeQuery(); ) {
            if (rs.next()) {
                int total = rs.getInt("total_rows");
                return total == 6;
            }
            return false;
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка валидации тестовых данных", e);
        }
    }
}
