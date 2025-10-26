/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.config;

import java.util.Properties;
import ru.mentee.power.exception.SASTException;

public class PostgresConfig implements DatabaseConfig {
    String url;
    String user;
    String password;
    String driver;
    boolean showSql;
    SASTException sastException;

    public PostgresConfig(Properties props) {
        this.url = props.getProperty("db.url");
        this.user = props.getProperty("db.username");
        this.password = props.getProperty("db.password");
        this.driver = props.getProperty("db.driver");
        this.showSql = Boolean.parseBoolean(props.getProperty("db.show-sql"));
        if (url == null) {
            System.out.println("Error: invalid configuration");
        }
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getUsername() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String getDriver() {
        return driver;
    }

    @Override
    public boolean getShowSql() {
        return showSql;
    }
}
