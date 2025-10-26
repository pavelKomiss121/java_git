/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.User;

/**
 * Repository для работы с пользователями через JDBC.
 * Обеспечивает операции поиска и подсчета пользователей.
 */
public interface UserRepository {

    /**
     * Находит всех пользователей, отсортированных по дате создания (DESC).
     * @return список всех пользователей
     * @throws DataAccessException при ошибках доступа к БД
     */
    List<User> findAll() throws DataAccessException;

    /**
     * Находит пользователя по ID.
     * @param id идентификатор пользователя
     * @return пользователь или Optional.empty()
     * @throws DataAccessException при ошибках доступа к БД
     */
    Optional<User> findById(Long id) throws DataAccessException;

    /**
     * Находит пользователя по email.
     * @param email email пользователя
     * @return пользователь или Optional.empty()
     * @throws DataAccessException при ошибках доступа к БД
     */
    Optional<User> findByEmail(String email) throws DataAccessException;

    /**
     * Находит пользователей, зарегистрированных после указанной даты.
     * @param registrationDate дата регистрации для фильтрации
     * @return список пользователей
     * @throws DataAccessException при ошибках доступа к БД
     */
    List<User> findByRegistrationDateAfter(LocalDate registrationDate) throws DataAccessException;

    /**
     * Находит пользователей, в имени которых содержится указанная подстрока.
     * @param namePart часть имени для поиска
     * @return список пользователей
     * @throws DataAccessException при ошибках доступа к БД
     */
    List<User> findByNameContaining(String namePart) throws DataAccessException;

    /**
     * Подсчитывает общее количество пользователей в системе.
     * @return количество пользователей
     * @throws DataAccessException при ошибках доступа к БД
     */
    long count() throws DataAccessException;
}
