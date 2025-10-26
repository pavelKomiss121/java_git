/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.config;

public interface DatabaseConfig {
    String DB_PASSWORD = "db.password";
    String DB_URL = "db.url";
    String DB_USERNAME = "db.username";
    String DB_DRIVER = "db.driver";
    String DB_SHOW_SQL = "db.show-sql";

    String getUrl();

    String getUsername();

    String getPassword();

    String getDriver();

    boolean getShowSql();
}
