/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.Order;
import ru.mentee.power.model.Product;
import ru.mentee.power.model.User;
import ru.mentee.power.model.mp162.IndexPerformanceTest;
import ru.mentee.power.model.mp162.IndexSizeInfo;

/**
 * Repository для демонстрации влияния B-Tree индексов на производительность.
 * Показывает разницу между запросами с индексами и без них.
 */
public interface BTreeIndexRepository {

    /**
     * Демонстрирует поиск пользователя по email без индекса.
     * Выполняет Seq Scan по всей таблице пользователей.
     *
     * @param email email пользователя для поиска
     * @return результат поиска с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    IndexPerformanceTest<Optional<User>> findUserByEmailWithoutIndex(String email)
            throws DataAccessException;

    /**
     * Демонстрирует поиск пользователя по email с B-Tree индексом.
     * Выполняет Index Scan для быстрого доступа.
     *
     * @param email email пользователя для поиска
     * @return результат поиска с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    IndexPerformanceTest<Optional<User>> findUserByEmailWithIndex(String email)
            throws DataAccessException;

    /**
     * Загружает заказы пользователя БЕЗ индекса на (user_id, created_at).
     *
     * @param userId идентификатор пользователя
     * @param limit количество заказов для загрузки
     * @param offset смещение для пагинации
     * @return заказы пользователя с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    IndexPerformanceTest<List<Order>> getUserOrdersWithoutIndex(
            Long userId, Integer limit, Integer offset) throws DataAccessException;

    /**
     * Загружает заказы пользователя С составным индексом на (user_id, created_at DESC).
     *
     * @param userId идентификатор пользователя
     * @param limit количество заказов для загрузки
     * @param offset смещение для пагинации
     * @return заказы пользователя с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    IndexPerformanceTest<List<Order>> getUserOrdersWithIndex(
            Long userId, Integer limit, Integer offset) throws DataAccessException;

    /**
     * Ищет товары по категории и диапазону цен БЕЗ индекса.
     *
     * @param categoryId идентификатор категории
     * @param minPrice минимальная цена
     * @param maxPrice максимальная цена
     * @param limit максимальное количество результатов
     * @return товары с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    IndexPerformanceTest<List<Product>> findProductsByCategoryAndPriceWithoutIndex(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer limit)
            throws DataAccessException;

    /**
     * Ищет товары по категории и диапазону цен С составным индексом.
     *
     * @param categoryId идентификатор категории
     * @param minPrice минимальная цена
     * @param maxPrice максимальная цена
     * @param limit максимальное количество результатов
     * @return товары с метриками производительности
     * @throws DataAccessException при ошибках доступа к данным
     */
    IndexPerformanceTest<List<Product>> findProductsByCategoryAndPriceWithIndex(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer limit)
            throws DataAccessException;

    /**
     * Создает B-Tree индексы для оптимизации производительности.
     *
     * @return результат создания индексов с временными метриками
     * @throws DataAccessException при ошибках создания индексов
     */
    IndexPerformanceTest<String> createBTreeIndexes() throws DataAccessException;

    /**
     * Удаляет B-Tree индексы для демонстрации деградации производительности.
     *
     * @return результат удаления индексов
     * @throws DataAccessException при ошибках удаления индексов
     */
    IndexPerformanceTest<String> dropBTreeIndexes() throws DataAccessException;

    /**
     * Получает размер индексов в байтах для анализа использования дискового пространства.
     *
     * @return информация о размерах индексов
     * @throws DataAccessException при ошибках запроса метаданных
     */
    List<IndexSizeInfo> getIndexSizeInformation() throws DataAccessException;
}
