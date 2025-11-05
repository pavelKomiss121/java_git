/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeadlockPreventionRecommendations {
    private List<String> immediateActions;
    private List<String> mediumTermImprovements;
    private List<String> longTermOptimizations;
    private String codeReviewGuidelines;
    private String databaseConfigurationTuning;
    private String monitoringSetupAdvice;
    private String applicationArchitectureRecommendations;
    private String estimatedImprovementPercentage;
}
