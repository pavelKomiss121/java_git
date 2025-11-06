/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp170;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SalesPersonRanking {
    private Long id;
    private String name;
    private String regionName;
    private BigDecimal totalSales;
    private Integer regionRank;
    private Integer denseRank;
    private Integer rowNumber;
    private BigDecimal marketSharePercent;
}
