/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.User;
import ru.mentee.power.repository.interfaces.UserRepository;

public class PostgresUserRepository implements UserRepository {

    private final ApplicationConfig config;

    public PostgresUserRepository(ApplicationConfig config) {
        this.config = config;
    }

    @Override
    public List<User> findAll() throws DataAccessException {
        String sql =
                "SELECT row_number() over (order by id) as id, name, email, created_at FROM users"
                        + " ORDER BY created_at DESC";
        List<User> users = new ArrayList<>();

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                User user =
                        User.builder()
                                .id(resultSet.getLong("id"))
                                .name(resultSet.getString("name"))
                                .email(resultSet.getString("email"))
                                .build();
                users.add(user);
            }

        } catch (Exception e) {
            throw new DataAccessException(
                    "Ошибка при получении пользователей: " + e.getMessage(), e);
        }
        return users;
    }

    @Override
    public Optional<User> findById(Long id) throws DataAccessException {
        String sql =
                "SELECT id, name, email, created_at FROM (SELECT row_number() over (order by id) as"
                        + " id, name, email, created_at FROM users) WHERE id = ?";

        if (id == null) {
            throw new DataAccessException("id не передали");
        }

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql); ) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    User user =
                            User.builder()
                                    .id(resultSet.getLong("id"))
                                    .name(resultSet.getString("name"))
                                    .email(resultSet.getString("email"))
                                    .build();
                    return Optional.of(user);
                }
            }

        } catch (Exception e) {
            throw new DataAccessException(
                    "Ошибка при получении пользователя: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) throws DataAccessException {
        String sql =
                "SELECT row_number() over (order by id) as id, name, email, created_at FROM users"
                        + " WHERE email = ?";

        if (email == null) {
            throw new DataAccessException("email не передали");
        }

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql); ) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    User user =
                            User.builder()
                                    .id(resultSet.getLong("id"))
                                    .name(resultSet.getString("name"))
                                    .email(resultSet.getString("email"))
                                    .build();
                    return Optional.of(user);
                }
            }
        } catch (Exception e) {
            throw new DataAccessException(
                    "Ошибка при получении пользователя: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<User> findByRegistrationDateAfter(LocalDate registrationDate)
            throws DataAccessException {

        if (registrationDate == null) {
            throw new DataAccessException("registrationDate не передали");
        }

        String sql =
                "SELECT row_number() over (order by id) as id, name, email, created_at FROM users"
                        + " WHERE created_at > ? ORDER BY created_at DESC";
        List<User> users = new ArrayList<>();

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql); ) {
            statement.setDate(1, java.sql.Date.valueOf(registrationDate));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    User user =
                            User.builder()
                                    .id(resultSet.getLong("id"))
                                    .name(resultSet.getString("name"))
                                    .email(resultSet.getString("email"))
                                    .build();
                    users.add(user);
                }
            }
        } catch (Exception e) {
            throw new DataAccessException(
                    "Ошибка при получении пользователей: " + e.getMessage(), e);
        }
        return users;
    }

    @Override
    public List<User> findByNameContaining(String namePart) throws DataAccessException {
        String sql =
                "SELECT row_number() over (order by id) as id, name, email, created_at FROM users"
                        + " WHERE name LIKE ?";
        List<User> users = new ArrayList<>();

        if (namePart == null) {
            throw new DataAccessException("namePart не передали");
        }

        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql); ) {
            statement.setString(1, "%" + namePart + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    User user =
                            User.builder()
                                    .id(resultSet.getLong("id"))
                                    .name(resultSet.getString("name"))
                                    .email(resultSet.getString("email"))
                                    .build();
                    users.add(user);
                }
            }
        } catch (Exception e) {
            throw new DataAccessException(
                    "Ошибка при получении пользователей: " + e.getMessage(), e);
        }
        return users;
    }

    @Override
    public long count() throws DataAccessException {
        String sql = "SELECT count(id) as count FROM users";
        try (Connection connection =
                        DriverManager.getConnection(
                                config.getUrl(), config.getUsername(), config.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getLong("count");
            }
        } catch (Exception e) {
            throw new DataAccessException(
                    "Ошибка при получении пользователей: " + e.getMessage(), e);
        }
        return 0;
    }
}
