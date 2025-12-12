/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp173;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Процессор для работы с большими наборами результатов через REFCURSOR.
 * Обеспечивает потоковую обработку данных и управление курсором.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultSetProcessor {
    private ResultSet resultSet;
    private Integer totalRows;
    private Integer processedRows;
    private Boolean hasMore;
    private String cursorName;

    /**
     * Получить следующую порцию данных.
     * @param batchSize размер порции
     * @return список строк данных
     */
    public List<Map<String, Object>> getNextBatch(int batchSize) throws SQLException {
        List<Map<String, Object>> batch = new ArrayList<>();
        if (resultSet == null) {
            return batch;
        }

        int count = 0;
        while (resultSet.next() && count < batchSize) {
            Map<String, Object> row = new HashMap<>();
            int columnCount = resultSet.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = resultSet.getMetaData().getColumnLabel(i);
                Object value = resultSet.getObject(i);
                row.put(columnName, value);
            }
            batch.add(row);
            count++;
            if (processedRows != null) {
                processedRows++;
            }
        }

        hasMore = resultSet.next();
        if (hasMore) {
            resultSet.previous();
        }

        return batch;
    }

    /**
     * Проверить наличие следующих данных.
     */
    public boolean hasNext() {
        return hasMore != null && hasMore;
    }
}
