/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.test;

import ru.mentee.power.exception.DataAccessException;

/**
 * Утилита для подготовки тестовых данных.
 * Помогает создавать консистентные тестовые сценарии.
 */
public interface TestDataPreparer {

    /**
     * Создать базовый набор пользователей для тестов.
     * @return количество созданных пользователей
     */
    int createTestUsers() throws DataAccessException;

    /**
     * Создать заказы для существующих пользователей.
     * @param ordersPerUser среднее количество заказов на пользователя
     * @return количество созданных заказов
     */
    int createTestOrders(int ordersPerUser) throws DataAccessException;

    /**
     * Создать товары и позиции заказов.
     * @return количество созданных товаров
     */
    int createTestProducts();

    /**
     * Очистить все тестовые данные в правильном порядке.
     */
    void cleanupAllData();
}
