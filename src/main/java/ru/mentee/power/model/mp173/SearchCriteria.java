/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp173;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Критерии поиска для запросов с большими результатами.
 * Содержит параметры фильтрации, сортировки и пагинации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchCriteria {
    private Map<String, Object> filters;
    private String sortColumn;
    private String sortDirection;
    private Integer pageNumber;
    private Integer pageSize;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String searchText;

    public void addFilter(String key, Object value) {
        if (this.filters == null) {
            this.filters = new HashMap<>();
        }
        this.filters.put(key, value);
    }
}
