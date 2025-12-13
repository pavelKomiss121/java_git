/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import java.sql.Connection;
import java.sql.SQLException;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DatabaseOperation {
    private String database;

    public Object execute(Connection connection) throws SQLException {
        return null;
    }
}
