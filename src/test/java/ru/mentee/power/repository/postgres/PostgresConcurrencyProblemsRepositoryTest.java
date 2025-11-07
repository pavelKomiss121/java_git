/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.mp167.ConcurrencyAnomalyResult;
import ru.mentee.power.model.mp167.MoneyTransferResult;
import ru.mentee.power.test.BaseIntegrationTest;

/**
 * Интеграционные тесты для демонстрации проблем конкурентности в PostgreSQL.
 *
 * Тесты проверяют корректность воспроизведения аномалий:
 * - Dirty Read (READ UNCOMMITTED)
 * - Non-repeatable Read (READ COMMITTED)
 * - Phantom Read (REPEATABLE READ)
 * - Lost Update (READ COMMITTED)
 *
 * Тесты также проверяют безопасные операции с правильными уровнями изоляции.
 *
 * Примечание: Тесты могут быть отключены (@Disabled), если урок уже пройден,
 * но они остаются для демонстрации и обучения.
 */
@DisplayName("Интеграционное тестирование проблем конкурентности")
@SuppressWarnings({"resource", "deprecation"})
public class PostgresConcurrencyProblemsRepositoryTest extends BaseIntegrationTest {

    private Liquibase liquibase;
    private PostgresConcurrencyProblemsRepository repository;

    @BeforeEach
    @Override
    protected void setUp() throws SASTException, IOException, DataAccessException {
        super.setUp();

        try (Connection conn = getTestConnection()) {
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            liquibase =
                    new Liquibase(
                            "db/migrations_161/changelog.yaml",
                            new ClassLoaderResourceAccessor(),
                            database);

            liquibase.update("dev,test"); // NOPMD - deprecated method used in tests

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        repository = new PostgresConcurrencyProblemsRepository(config);
    }

    @Test
    @DisplayName("Демонстрация Dirty Read - незафиксированные данные")
    void shouldDemonstrateDirtyRead() throws DataAccessException {
        // Подготовка: создаем счет с начальным балансом
        Long accountId = createAccount(1L, new BigDecimal("1000.00"));
        BigDecimal initialBalance = getAccountBalanceDirectly(accountId);

        // Выполняем демонстрацию dirty read
        BigDecimal amountToChange = new BigDecimal("500.00");
        ConcurrencyAnomalyResult result =
                repository.demonstrateDirtyRead(accountId, amountToChange);

        // Проверяем результаты
        assertThat(result).isNotNull();
        assertThat(result.getAnomalyType()).isEqualTo("DIRTY_READ");
        assertThat(result.getIsolationLevel()).isEqualTo("READ UNCOMMITTED");
        assertThat(result.getExecutionSteps()).isNotEmpty();
        assertThat(result.getExecutionSteps().size()).isGreaterThanOrEqualTo(5);

        // Проверяем, что промежуточное значение отличается от начального и финального
        BigDecimal initial = new BigDecimal(result.getInitialValue());
        BigDecimal intermediate = new BigDecimal(result.getIntermediateValue());
        BigDecimal finalValue = new BigDecimal(result.getFinalValue());

        // При dirty read промежуточное значение должно отличаться от финального
        if (result.getAnomalyDetected()) {
            assertThat(intermediate).isNotEqualTo(initial);
            assertThat(intermediate).isNotEqualTo(finalValue);
            assertThat(finalValue)
                    .isEqualTo(initial); // Финальное значение должно быть как начальное
        }

        // Проверяем, что финальный баланс в БД соответствует начальному (т.к. транзакция
        // откатилась)
        BigDecimal actualFinalBalance = getAccountBalanceDirectly(accountId);
        assertThat(actualFinalBalance).isEqualByComparingTo(initialBalance);

        // Проверяем наличие рекомендаций
        assertThat(result.getPreventionRecommendations()).isNotEmpty();
        assertThat(result.getDetailedDescription()).isNotBlank();
    }

    @Test
    @DisplayName("Демонстрация Non-repeatable Read - изменение данных между чтениями")
    void shouldDemonstrateNonRepeatableRead() throws DataAccessException {
        // Подготовка: создаем счет с начальным балансом
        Long accountId = createAccount(1L, new BigDecimal("2000.00"));
        BigDecimal initialBalance = getAccountBalanceDirectly(accountId);

        // Выполняем демонстрацию non-repeatable read
        BigDecimal amountToChange = new BigDecimal("300.00");
        ConcurrencyAnomalyResult result =
                repository.demonstrateNonRepeatableRead(accountId, amountToChange);

        // Проверяем результаты
        assertThat(result).isNotNull();
        assertThat(result.getAnomalyType()).isEqualTo("NON_REPEATABLE_READ");
        assertThat(result.getIsolationLevel()).isEqualTo("READ COMMITTED");
        assertThat(result.getExecutionSteps()).isNotEmpty();

        // Проверяем, что значения изменились между чтениями
        BigDecimal firstRead = new BigDecimal(result.getInitialValue());
        BigDecimal secondRead = new BigDecimal(result.getIntermediateValue());

        // При non-repeatable read второе чтение должно отличаться от первого
        if (result.getAnomalyDetected()) {
            assertThat(secondRead).isNotEqualTo(firstRead);
            // Второе чтение должно быть больше на amountToChange
            assertThat(secondRead).isEqualByComparingTo(firstRead.add(amountToChange));
        }

        // Проверяем финальный баланс в БД
        BigDecimal actualFinalBalance = getAccountBalanceDirectly(accountId);
        assertThat(actualFinalBalance).isEqualByComparingTo(initialBalance.add(amountToChange));

        // Проверяем наличие рекомендаций
        assertThat(result.getPreventionRecommendations()).isNotEmpty();
    }

    @Test
    @DisplayName("Демонстрация Phantom Read - появление новых записей")
    void shouldDemonstratePhantomRead() throws DataAccessException {
        // Подготовка: создаем счет и несколько транзакций
        Long accountId = createAccount(1L, new BigDecimal("5000.00"));
        BigDecimal thresholdAmount = new BigDecimal("1000.00");
        BigDecimal newTransactionAmount = new BigDecimal("1500.00");

        // Создаем несколько транзакций ниже порога
        createTestTransaction(accountId, new BigDecimal("500.00"));
        createTestTransaction(accountId, new BigDecimal("800.00"));

        // Выполняем демонстрацию phantom read
        ConcurrencyAnomalyResult result =
                repository.demonstratePhantomRead(accountId, thresholdAmount, newTransactionAmount);

        // Проверяем результаты
        assertThat(result).isNotNull();
        assertThat(result.getAnomalyType()).isEqualTo("PHANTOM_READ");
        assertThat(result.getIsolationLevel()).isEqualTo("REPEATABLE READ");
        assertThat(result.getExecutionSteps()).isNotEmpty();

        // Проверяем изменение количества записей
        String firstCountStr = result.getInitialValue().replaceAll("[^0-9]", "");
        String secondCountStr = result.getIntermediateValue().replaceAll("[^0-9]", "");

        if (!firstCountStr.isEmpty() && !secondCountStr.isEmpty()) {
            int firstCount = Integer.parseInt(firstCountStr);
            int secondCount = Integer.parseInt(secondCountStr);

            // При phantom read может появиться новая запись
            // Поведение зависит от уровня изоляции (REPEATABLE READ может предотвратить phantom
            // read)
            if (result.getAnomalyDetected()) {
                assertThat(secondCount).isGreaterThan(firstCount);
            }
        }

        // Проверяем наличие рекомендаций
        assertThat(result.getPreventionRecommendations()).isNotEmpty();
    }

    @Test
    @DisplayName("Демонстрация Lost Update - потеря обновления при конкурентных изменениях")
    void shouldDemonstrateLostUpdate() throws DataAccessException {
        // Подготовка: создаем счет с начальным балансом
        Long accountId = createAccount(1L, new BigDecimal("1000.00"));
        BigDecimal initialBalance = getAccountBalanceDirectly(accountId);

        // Выполняем демонстрацию lost update
        BigDecimal firstAmount = new BigDecimal("200.00");
        BigDecimal secondAmount = new BigDecimal("300.00");
        ConcurrencyAnomalyResult result =
                repository.demonstrateLostUpdate(accountId, firstAmount, secondAmount);

        // Проверяем результаты
        assertThat(result).isNotNull();
        assertThat(result.getAnomalyType()).isEqualTo("LOST_UPDATE");
        assertThat(result.getIsolationLevel()).isEqualTo("READ COMMITTED");
        assertThat(result.getExecutionSteps()).isNotEmpty();

        // Проверяем финальный баланс
        BigDecimal finalBalance = new BigDecimal(result.getFinalValue());
        BigDecimal expectedBalance = initialBalance.add(firstAmount).add(secondAmount);

        // При lost update финальный баланс может не совпадать с ожидаемым
        if (result.getAnomalyDetected()) {
            assertThat(finalBalance).isNotEqualByComparingTo(expectedBalance);
            // Одно из обновлений потеряно
            assertThat(finalBalance)
                    .isIn(initialBalance.add(firstAmount), initialBalance.add(secondAmount));
        }

        // Проверяем фактический баланс в БД
        BigDecimal actualFinalBalance = getAccountBalanceDirectly(accountId);
        // Может быть потеряно одно из обновлений
        assertThat(actualFinalBalance)
                .isIn(
                        initialBalance.add(firstAmount),
                        initialBalance.add(secondAmount),
                        expectedBalance);

        // Проверяем наличие рекомендаций
        assertThat(result.getPreventionRecommendations()).isNotEmpty();
    }

    @Test
    @DisplayName("Безопасный перевод денег с правильным уровнем изоляции")
    void shouldPerformSafeMoneyTransfer() throws DataAccessException {
        // Подготовка: создаем два счета
        Long fromAccountId = createAccount(1L, new BigDecimal("5000.00"));
        Long toAccountId = createAccount(2L, new BigDecimal("1000.00"));

        BigDecimal fromInitialBalance = getAccountBalanceDirectly(fromAccountId);
        BigDecimal toInitialBalance = getAccountBalanceDirectly(toAccountId);
        BigDecimal transferAmount = new BigDecimal("1500.00");

        // Выполняем безопасный перевод
        MoneyTransferResult result =
                repository.safeMoneyTransfer(fromAccountId, toAccountId, transferAmount);

        // Проверяем результаты
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isIn("SUCCESS", "SERIALIZATION_FAILURE");
        assertThat(result.getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(result.getIsolationLevel()).isEqualTo("SERIALIZABLE");

        if ("SUCCESS".equals(result.getStatus())) {
            // Проверяем балансы до и после
            assertThat(result.getFromAccountBalanceBefore())
                    .isEqualByComparingTo(fromInitialBalance);
            assertThat(result.getToAccountBalanceBefore()).isEqualByComparingTo(toInitialBalance);
            assertThat(result.getFromAccountBalanceAfter())
                    .isEqualByComparingTo(fromInitialBalance.subtract(transferAmount));
            assertThat(result.getToAccountBalanceAfter())
                    .isEqualByComparingTo(toInitialBalance.add(transferAmount));

            // Проверяем фактический баланс в БД
            BigDecimal fromActualBalance = getAccountBalanceDirectly(fromAccountId);
            BigDecimal toActualBalance = getAccountBalanceDirectly(toAccountId);

            assertThat(fromActualBalance)
                    .isEqualByComparingTo(fromInitialBalance.subtract(transferAmount));
            assertThat(toActualBalance).isEqualByComparingTo(toInitialBalance.add(transferAmount));
        }

        // Проверяем время выполнения
        assertThat(result.getExecutionDurationMillis()).isNotNull();
        assertThat(result.getExecutionDurationMillis()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("Безопасный перевод - недостаточно средств")
    void shouldFailSafeMoneyTransferWhenInsufficientFunds() throws DataAccessException {
        // Подготовка: создаем счет с недостаточным балансом
        Long fromAccountId = createAccount(1L, new BigDecimal("100.00"));
        Long toAccountId = createAccount(2L, new BigDecimal("1000.00"));

        BigDecimal transferAmount = new BigDecimal("500.00");

        // Выполняем перевод большей суммы, чем есть на счете
        MoneyTransferResult result =
                repository.safeMoneyTransfer(fromAccountId, toAccountId, transferAmount);

        // Проверяем, что операция завершилась неудачей
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getFromAccountBalanceBefore()).isNotNull();

        // Проверяем, что балансы не изменились
        BigDecimal fromActualBalance = getAccountBalanceDirectly(fromAccountId);
        BigDecimal toActualBalance = getAccountBalanceDirectly(toAccountId);

        assertThat(fromActualBalance).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(toActualBalance).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Получение информации об уровне изоляции")
    void shouldGetCurrentIsolationLevelInfo() throws DataAccessException {
        // Выполняем запрос информации
        String info = repository.getCurrentIsolationLevelInfo();

        // Проверяем результаты
        assertThat(info).isNotBlank();
        assertThat(info).contains("PostgreSQL Isolation Level Information");
        assertThat(info).contains("READ COMMITTED");
        assertThat(info).contains("REPEATABLE READ");
        assertThat(info).contains("SERIALIZABLE");
        assertThat(info).contains("Supported Isolation Levels");
    }

    // Вспомогательные методы

    private Long createAccount(Long userId, BigDecimal initialBalance) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO mentee_power.accounts (user_id, balance)"
                                        + " VALUES (?, ?) RETURNING id",
                                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, userId);
            stmt.setBigDecimal(2, initialBalance);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка создания счета", e);
        }
        throw new RuntimeException("Не удалось создать счет");
    }

    private BigDecimal getAccountBalanceDirectly(Long accountId) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT balance FROM mentee_power.accounts WHERE id = ?")) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения баланса", e);
        }
        return BigDecimal.ZERO;
    }

    private void createTestTransaction(Long accountId, BigDecimal amount) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO mentee_power.transactions (account_id, amount,"
                                    + " transaction_type, status, description, created_at) VALUES"
                                    + " (?, ?, 'DEPOSIT', 'COMPLETED', 'Test transaction',"
                                    + " NOW())")) {
            stmt.setLong(1, accountId);
            stmt.setBigDecimal(2, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка создания транзакции", e);
        }
    }
}
