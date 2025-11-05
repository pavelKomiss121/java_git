/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp168;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LockAnalysisData {
    private List<String> frequentQueries;
    private Map<String, Integer> tableAccessPatterns;
    private Integer averageTransactionDuration;
    private Integer peakConcurrentUsers;
    private String applicationWorkloadType; // OLTP, OLAP, MIXED
}
