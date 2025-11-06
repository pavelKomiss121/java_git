/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserActivitySummary {
    private String activityType;
    private LocalDateTime activityDate;
    private BigDecimal activityValue;
    private String description;
    private String status;
    private Map<String, Object> additionalData;
}
