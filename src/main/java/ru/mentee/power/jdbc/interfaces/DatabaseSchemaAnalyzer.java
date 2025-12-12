/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.jdbc.interfaces;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import ru.mentee.power.model.mp173.SchemaInfo;
import ru.mentee.power.model.mp173.TableOptimizationInfo;
import ru.mentee.power.model.mp173.TableStatistics;

/**
 * Анализатор схемы базы данных через DatabaseMetaData.
 */
public interface DatabaseSchemaAnalyzer {

    /**
     * Получить полную информацию о структуре БД.
     * @return информация о схеме, таблицах, индексах
     */
    SchemaInfo analyzeDatabaseSchema() throws SQLException;

    /**
     * Найти все таблицы с отсутствующими индексами на FK.
     * @return список таблиц требующих оптимизации
     */
    List<TableOptimizationInfo> findMissingIndexes() throws SQLException;

    /**
     * Получить статистику использования таблиц.
     * @return карта таблица -> статистика
     */
    Map<String, TableStatistics> getTableStatistics() throws SQLException;
}
