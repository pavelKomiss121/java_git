/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp162;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndexSizeInfo {
    private String indexName;
    private String tableName;
    private String indexType;
    private Long sizeBytes;
    private String sizeHuman; // e.g., "2.5 MB"
    private Long tuples;
    private String definition;
}
