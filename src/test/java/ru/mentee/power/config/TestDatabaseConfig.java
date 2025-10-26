/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.User;
import ru.mentee.power.repository.PostgresUserRepository;

public class TestDatabaseConfig {
    private PostgresUserRepository repository;
    private ApplicationConfig config;

    @BeforeEach
    void setUp() throws IOException, SASTException {
        Properties properties = loadTestProperties();

        // Создаем конфигурацию без загрузки файлов
        this.config =
                new ApplicationConfig(properties, new ConfigFilePath()) {
                    @Override
                    public void load(String path) {
                        // Не загружаем файлы, используем уже загруженные properties
                    }
                };

        repository = new PostgresUserRepository(config);
        initializeTestDatabase();
    }

    private Properties loadTestProperties() throws IOException {
        Properties properties = new Properties();
        String resourcePath = "application-test.properties";

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Файл " + resourcePath + " не найден в classpath");
            }
            properties.load(input);
        }
        return properties;
    }

    private void initializeTestDatabase() {
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                Statement statement = connection.createStatement()) {

            String sqlScript = loadResource("test-schema.sql");
            statement.execute(sqlScript);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации тестовой БД", e);
        }
    }

    private String loadResource(String resourcePath) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Файл " + resourcePath + " не найден в classpath");
            }

            byte[] bytes = input.readAllBytes();
            return new String(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения ресурса " + resourcePath, e);
        }
    }

    @Test
    void shouldFindAllUsersOrderedByCreationDate() throws DataAccessException {
        List<User> users = repository.findAll();

        assertThat(users).isNotEmpty();
        assertThat(users).hasSize(5);

        for (int i = 0; i < users.size() - 1; i++) {
            assertThat(users.get(i).getCreatedAt())
                    .isAfterOrEqualTo(users.get(i + 1).getCreatedAt());
        }

        User newestUser = users.get(0);
        assertThat(newestUser.getId()).isNotNull();
        assertThat(newestUser.getName()).isNotNull();
        assertThat(newestUser.getEmail()).isNotNull();
    }

    @Test
    void shouldFindUserByExistingId() throws DataAccessException {
        Optional<User> userOptional = repository.findById(1L);

        assertThat(userOptional).isPresent();
        User user = userOptional.get();
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isNotNull();
        assertThat(user.getEmail()).isNotNull();
    }

    @Test
    void shouldReturnEmptyOptionalForNonExistingId() throws DataAccessException {
        Optional<User> userOptional = repository.findById(999L);

        assertThat(userOptional).isEmpty();
    }

    @Test
    void shouldFindUserByEmail() throws DataAccessException {
        Optional<User> userOptional = repository.findByEmail("john.doe@example.com");

        assertThat(userOptional).isPresent();
        User user = userOptional.get();
        assertThat(user.getEmail()).isEqualTo("john.doe@example.com");

        Optional<User> nonExistent = repository.findByEmail("nonexistent@example.com");
        assertThat(nonExistent).isEmpty();
    }

    @Test
    void shouldFindUsersByNameContaining() throws DataAccessException {
        List<User> users = repository.findByNameContaining("John Doe");

        assertThat(users).isNotEmpty();
        users.forEach(
                user -> {
                    assertThat(user.getName()).containsIgnoringCase("John Doe");
                });
    }

    @Test
    void shouldCountAllUsersCorrectly() throws DataAccessException {
        long count = repository.count();

        assertThat(count).isEqualTo(5);
    }

    @Test
    void shouldFindUsersRegisteredAfterSpecificDate() throws DataAccessException {
        LocalDate date = LocalDate.of(2024, 1, 1);
        List<User> users = repository.findByRegistrationDateAfter(date);

        assertThat(users).isNotEmpty();
        users.forEach(
                user -> {
                    assertThat(user.getCreatedAt().toLocalDate()).isAfter(date);
                });
    }

    @Test
    void shouldHandleNullParametersGracefully() {
        assertThatThrownBy(() -> repository.findById(null)).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> repository.findByEmail(null))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> repository.findByNameContaining(null))
                .isInstanceOf(DataAccessException.class);
    }
}
