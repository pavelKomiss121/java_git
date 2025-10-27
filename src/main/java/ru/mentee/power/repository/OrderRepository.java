/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository;

import java.util.List;
import ru.mentee.power.entity.MonthlyOrderStats;
import ru.mentee.power.entity.OrderAnalytics;
import ru.mentee.power.exception.DataAccessException;

/**
 * Repository для работы с аналитикой заказов.
 * Предоставляет методы для агрегации и анализа данных.
 */
public interface OrderRepository {

    /**
     * Получить аналитику активности пользователей.
     * Группирует заказы по пользователям, вычисляет агрегатные метрики.
     *
     * @return список аналитических данных по пользователям
     * @throws DataAccessException если произошла ошибка при работе с БД
     */
    List<OrderAnalytics> getUserAnalytics() throws DataAccessException;

    /**
     * Найти топ активных покупателей.
     * Возвращает пользователей с наибольшей суммой заказов.
     *
     * @param limit количество пользователей в топе
     * @return список топ покупателей
     * @throws DataAccessException если произошла ошибка при работе с БД
     */
    List<OrderAnalytics> getTopCustomers(int limit) throws DataAccessException;

    /**
     * Получить месячную статистику заказов.
     * Группирует заказы по месяцам, вычисляет количество и выручку.
     *
     * @return список статистики по месяцам
     * @throws DataAccessException если произошла ошибка при работе с БД
     */
    List<MonthlyOrderStats> getMonthlyOrderStats() throws DataAccessException;
}
