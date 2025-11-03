/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.model.mp164;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для информации об активных запросах.
 * Содержит информацию о текущих выполняющихся запросах в базе данных.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveQueryInfo {
    private Integer pid;
    private String databaseName;
    private String userName;
    private String clientAddress;
    private LocalDateTime queryStart;
    private String state;
    private String query;
    private BigDecimal duration;
    private Boolean isBlocking;
    private List<Integer> blockedBy;
}

