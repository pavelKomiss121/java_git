/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp166;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для информации о phantom reads.
 * Содержит данные о появлении новых записей между повторными чтениями.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhantomReadResult {
    private String sessionId;
    private String isolationLevel;
    private Integer firstReadCount;
    private Integer secondReadCount;
    private Boolean phantomReadDetected;
    private LocalDateTime firstReadTime;
    private LocalDateTime secondReadTime;
    private Integer newRecordsCount;
    private String query;
}

