/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.migration;

import org.testcontainers.containers.PostgreSQLContainer;
import ru.mentee.power.exception.DataAccessException;

/**
 * Валидатор для проверки корректности миграций.
 * Используется в тестах для проверки целостности схемы.
 */
public interface MigrationValidator {

    boolean validateSchemaStructure(PostgreSQLContainer postgres) throws DataAccessException;

    boolean validateForeignKeyConstraints(PostgreSQLContainer postgres) throws DataAccessException;

    boolean validateIndexes(PostgreSQLContainer postgres) throws DataAccessException;

    boolean validateTestData(PostgreSQLContainer postgres) throws DataAccessException;
}
