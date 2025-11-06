/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp169;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpsellingOpportunity {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String currentCustomerTier;
    private String targetCustomerTier;
    private BigDecimal currentTotalSpent;
    private BigDecimal targetTierMinimumSpent;
    private BigDecimal spendingGap;
    private Integer currentOrderCount;
    private BigDecimal averageOrderValue;
    private LocalDateTime lastPurchaseDate;
    private List<String> recommendedProducts;
    private List<String> recommendedCategories;
    private BigDecimal estimatedUpsellPotential;
    private String recommendationReason;
    private Integer confidenceScore;
}
