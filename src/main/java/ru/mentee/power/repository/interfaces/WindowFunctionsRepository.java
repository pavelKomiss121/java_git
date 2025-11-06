/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.util.List;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp170.DailySalesReport;
import ru.mentee.power.model.mp170.SalesPersonRanking;

/**
 * Repository для демонстрации оконных функций (Window Functions) в PostgreSQL.
 * Показывает использование OVER, PARTITION BY, RANK, DENSE_RANK, ROW_NUMBER и других оконных функций.
 */
public interface WindowFunctionsRepository {

    /**
     * Выполняет запрос с ранжированием продавцов по регионам.
     * Использует RANK, DENSE_RANK и ROW_NUMBER для различных типов ранжирования.
     *
     * @return список продавцов с рангами
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<SalesPersonRanking> executeRankingQuery() throws DataAccessException;

    /**
     * Вычисляет накопительные суммы продаж по дням.
     * Использует SUM() OVER (ORDER BY) для расчета running totals.
     *
     * @return список ежедневных отчетов с накопительными суммами
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<DailySalesReport> calculateRunningTotals() throws DataAccessException;

    /**
     * Анализирует производительность продавцов по регионам.
     * Сравнивает индивидуальные показатели с региональными средними.
     *
     * @return список продавцов с региональной аналитикой
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<SalesPersonRanking> analyzeRegionalPerformance() throws DataAccessException;

    /**
     * Генерирует распределение продавцов по квартилям.
     * Использует NTILE() для разделения на группы.
     *
     * @param quartileNumber номер квартиля (1-4)
     * @return список продавцов в указанном квартиле
     * @throws DataAccessException при ошибках доступа к данным
     */
    List<SalesPersonRanking> generateQuartileDistribution(Integer quartileNumber)
            throws DataAccessException;
}
