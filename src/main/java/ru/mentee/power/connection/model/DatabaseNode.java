/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection.model;

import lombok.Builder;
import lombok.Value;

/**
 * Узел базы данных для failover конфигурации.
 */
@Value
@Builder
public class DatabaseNode {
    String jdbcUrl;
    String username;
    String password;
    String driver;
    String nodeName;
    int priority;
    boolean enabled;
}
