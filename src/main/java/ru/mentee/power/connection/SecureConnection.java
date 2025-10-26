/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.config.ConfigFilePath;

@Slf4j
public class SecureConnection {

    public static void main(String[] args) {
        try {
            ApplicationConfig config =
                    new ApplicationConfig(new Properties(), new ConfigFilePath());
            log.info(
                    "Безопасное подключение приложения {} к PostgreSQL...",
                    config.getApplicationName());
            try (Connection connection =
                            DriverManager.getConnection(
                                    config.getUrl(), config.getUsername(), config.getPassword());
                    Statement statement = connection.createStatement()) {
                log.info("Соединение установлено успешно!");
                String sqlQuery = "SELECT COUNT(*) as user_count FROM users";
                if (config.getShowSql()) {
                    log.debug("SQL: {}", sqlQuery);
                }
                try (ResultSet resultSet = statement.executeQuery(sqlQuery)) {
                    if (resultSet.next()) {
                        int userCount = resultSet.getInt("user_count");
                        log.info("Пользователей в базе: {}", userCount);
                    }
                }
                log.info("Конфигурация работает! Пароли в безопасности.");
            }

        } catch (Exception e) {
            log.error("Ошибка подключения: {}", e.getMessage(), e);
            if (e.getMessage().contains(".properties")) {
                log.error("Подсказка: проверьте файлы конфигурации ресурсов src/main/resources");
            }
        }
    }
}
