/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.jdbc.interfaces;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Динамический процессор SQL запросов с ResultSetMetaData.
 */
public interface DynamicQueryProcessor {

    /**
     * Выполнить произвольный SQL с динамическим маппингом.
     * @param sql SQL запрос
     * @return список строк как Map колонка->значение
     */
    List<Map<String, Object>> executeDynamicQuery(String sql) throws SQLException;

    /**
     * Экспортировать результаты запроса в CSV.
     * @param sql SQL запрос
     * @param outputPath путь для сохранения CSV
     * @return количество экспортированных строк
     */
    int exportQueryToCSV(String sql, String outputPath) throws SQLException;

    /**
     * Создать Java класс на основе структуры ResultSet.
     * @param sql SQL запрос для анализа
     * @param className имя генерируемого класса
     * @return сгенерированный Java код
     */
    String generateEntityClass(String sql, String className) throws SQLException;
}
