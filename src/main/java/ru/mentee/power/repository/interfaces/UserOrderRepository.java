/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository;

import java.math.BigDecimal;
import java.util.List;
import ru.mentee.power.entity.*;
import ru.mentee.power.exception.DataAccessException;

/**
 * Repository для работы с JOIN запросами пользователей и заказов.
 */
public interface UserOrderRepository {

    /**
     * Найти пользователей с общей суммой заказов больше указанной.
     * Использует INNER JOIN между users и orders для расчета.
     *
     * @param minTotal минимальная сумма заказов
     * @return список пользователей с суммами заказов
     * @throws DataAccessException если произошла ошибка при работе с БД
     */
    List<UserOrderSummary> findUsersWithTotalAbove(BigDecimal minTotal) throws DataAccessException;

    /**
     * Найти всех пользователей и количество их заказов.
     * Использует LEFT JOIN чтобы включить пользователей без заказов.
     *
     * @return список всех пользователей с количеством заказов (может быть 0)
     * @throws DataAccessException если произошла ошибка при работе с БД
     */
    List<UserOrderCount> getAllUsersWithOrderCount() throws DataAccessException;

    /**
     * Найти топ продаваемые товары с информацией о количестве заказов.
     * Использует JOIN между products, order_items и orders.
     *
     * @param limit количество товаров в топе
     * @return список топ товаров отсортированный по популярности
     * @throws DataAccessException если произошла ошибка при работе с БД
     */
    List<ProductSalesInfo> getTopSellingProducts(int limit) throws DataAccessException;
}
