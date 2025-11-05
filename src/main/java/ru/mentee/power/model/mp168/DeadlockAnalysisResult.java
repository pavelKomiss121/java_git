/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeadlockAnalysisResult {
    private Integer totalDeadlocks;
    private LocalDateTime analysisStartTime;
    private LocalDateTime analysisEndTime;
    private Map<String, Integer> deadlockPatterns;
    private List<String> mostCommonCauses;
    private List<String> affectedTables;
    private List<String> preventionRecommendations;
    private Double deadlocksPerHour;
    private String trendAnalysis;
    private String systemHealthAssessment;
}
