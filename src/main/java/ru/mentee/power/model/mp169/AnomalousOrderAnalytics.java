/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnomalousOrderAnalytics {
    private Long orderId;
    private Long userId;
    private String userEmail;
    private String userName;
    private BigDecimal orderAmount;
    private BigDecimal userAverageOrderAmount;
    private BigDecimal anomalyCoefficient;
    private String anomalyType;
    private Integer itemCount;
    private List<String> productCategories;
    private String possibleExplanation;
    private Boolean requiresReview;
}
