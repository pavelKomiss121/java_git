/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.exception.SASTException;

@Slf4j
public class ApplicationConfig implements DatabaseConfig, Overridable, Fileable {
    public static final String APP_NAME = "app.name";

    private final DatabaseConfig dbConfig;
    private final Properties properties;
    private final SecureValidator validator;

    public ApplicationConfig(Properties properties, ConfigFilePath configFilePath)
            throws IOException, SASTException {
        this.properties = properties;
        this.validator = new SecureValidator(properties);
        load(configFilePath.getAppMainConfigPath());
        validator.validate();
        try {
            load(configFilePath.getAppSecretPath());
        } catch (IOException notFound) {
            log.error(
                    "Файл секретов {} не обнаружен, секреты будут загружены из ENV",
                    configFilePath.getAppSecretPath(),
                    notFound);
        }
        this.dbConfig = new PostgresConfig(properties);
        override();
    }

    public String getApplicationName() {
        return properties.getProperty(APP_NAME);
    }

    public String getUrl() {
        return dbConfig.getUrl();
    }

    public String getUsername() {
        return dbConfig.getUsername();
    }

    public String getPassword() {
        return dbConfig.getPassword();
    }

    public String getDriver() {
        return dbConfig.getDriver();
    }

    public boolean getShowSql() {
        return dbConfig.getShowSql();
    }

    @Override
    public void load(String pathProperties) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(pathProperties)) {
            if (input == null) {
                throw new IOException("Файл не найден: %s".formatted(pathProperties));
            }
            properties.load(input);
        }
    }

    @Override
    public void override() {
        String envUrl = System.getenv(DB_URL);
        String envUsername = System.getenv(DB_USERNAME);
        String envPassword = System.getenv(DB_PASSWORD);
        String envDriver = System.getenv(DB_DRIVER);
        if (envUrl != null) {
            properties.setProperty(DB_URL, envUrl);
            log.info("{} переопределен из Environment Variable", DB_USERNAME);
        }
        if (envUsername != null) {
            properties.setProperty(DB_USERNAME, envUsername);
            log.info("{} переопределен из Environment Variable", DB_USERNAME);
        }
        if (envPassword != null) {
            properties.setProperty(DB_PASSWORD, envPassword);
            log.info("{} переопределен из Environment Variable", DB_PASSWORD);
        }
        if (envDriver != null) {
            properties.setProperty(DB_DRIVER, envDriver);
            log.info("{} переопределен из Environment Variable", DB_DRIVER);
        }
    }
}
