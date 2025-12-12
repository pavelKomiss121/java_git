/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.jdbc.interfaces;

import java.sql.SQLException;
import java.util.List;
import ru.mentee.power.model.mp173.BatchProcedureResult;
import ru.mentee.power.model.mp173.ResultSetProcessor;
import ru.mentee.power.model.mp173.SearchCriteria;
import ru.mentee.power.model.mp173.UpdateRequest;
import ru.mentee.power.model.mp173.UserStatistics;

/**
 * Процессор хранимых процедур через CallableStatement.
 */
public interface StoredProcedureProcessor {

    /**
     * Вызвать процедуру расчета статистики пользователя.
     * @param userId ID пользователя
     * @return статистика пользователя
     */
    UserStatistics calculateUserStatistics(Long userId) throws SQLException;

    /**
     * Выполнить batch обновление через хранимую процедуру.
     * @param updates список обновлений
     * @return результаты выполнения
     */
    BatchProcedureResult executeBatchProcedure(List<UpdateRequest> updates) throws SQLException;

    /**
     * Вызвать процедуру с REFCURSOR для больших результатов.
     * @param criteria критерии поиска
     * @return курсор с результатами
     */
    ResultSetProcessor getLargeResultSet(SearchCriteria criteria) throws SQLException;
}
