/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.config;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mentee.power.exception.SASTException;

public class SecureValidator {
    private static final Logger logger = LoggerFactory.getLogger(SecureValidator.class);

    private final Properties properties;
    private final String[] weakPasswords = {
        "password", "123456", "admin", "root", "qwerty",
        "12345", "1234", "123", "password123", "admin123"
    };

    public SecureValidator(Properties properties) {
        this.properties = properties;
    }

    public void validate() throws SASTException {
        logger.info("🔍 Starting security validation...");

        validateNoPasswordInMainConfig();

        validatePasswordStrength();

        validateOtherVulnerabilities();

        logger.info("✅ Security validation completed successfully");
    }

    private void validateNoPasswordInMainConfig() throws SASTException {
        if (properties.containsKey("db.password")) {
            logger.error("🔐 SECURITY ISSUE: Password found in main configuration file");
            throw new SASTException(
                    "Переменная db.password не должна быть записана в конфигурации приложения");
        }
    }

    private void validatePasswordStrength() throws SASTException {
        String password = properties.getProperty("db.password");

        if (password == null || password.trim().isEmpty()) {
            logger.warn("⚠️ WARNING: Database password is not set");
            return;
        }

        for (String weak : weakPasswords) {
            if (password.equals(weak)) {
                logger.error("🔐 SECURITY ISSUE: Weak password detected: {}", password);
                throw new SASTException("Обнаружен слабый пароль: " + password);
            }
        }

        if (password.length() < 8) {
            logger.error("🔐 SECURITY ISSUE: Password too short (less than 8 characters)");
            throw new SASTException("Пароль слишком короткий (менее 8 символов)");
        }

        logger.info("✅ Password strength validation passed");
    }

    private void validateOtherVulnerabilities() throws SASTException {
        String url = properties.getProperty("db.url");
        if (url != null && url.contains("localhost")) {
            logger.info("ℹ️ INFO: Using localhost database connection");
        }

        String username = properties.getProperty("db.username");
        if (username == null || username.trim().isEmpty()) {
            logger.error("🔐 SECURITY ISSUE: Database username is not set");
            throw new SASTException("Имя пользователя базы данных не установлено");
        }

        String driver = properties.getProperty("db.driver");
        if (driver == null || driver.trim().isEmpty()) {
            logger.error("🔐 SECURITY ISSUE: Database driver is not set");
            throw new SASTException("Драйвер базы данных не установлен");
        }

        logger.info("✅ Other security validations passed");
    }
}
