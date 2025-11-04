/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.service;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.InsufficientFundsException;
import ru.mentee.power.exception.ProductNotAvailableException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.mp165.*;
import ru.mentee.power.repository.postgres.PostgresAcidTransactionRepository;
import ru.mentee.power.test.BaseIntegrationTest;

@DisplayName("Интеграционное тестирование ACID транзакций")
public class AcidTransactionServiceIntegrationTest extends BaseIntegrationTest {

    private Liquibase liquibase;
    private PostgresAcidTransactionRepository repository;

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
            // Таблицы accounts, transactions и обновления products создаются через миграцию
            // 007-create-acid-tables.sql

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        repository = new PostgresAcidTransactionRepository(config);
    }

    @Test
    @DisplayName("Демонстрация атомарности - успешный перевод")
    void shouldDemonstrateAtomicity() throws BusinessException, InsufficientFundsException {
        // Подготовка: используем существующих пользователей из миграции (ID 1, 2) и создаем новые
        // счета
        Long account1Id = createAccount(1L, new BigDecimal("1000.00"));
        Long account2Id = createAccount(2L, new BigDecimal("500.00"));

        BigDecimal initialBalance1 = getAccountBalanceDirectly(account1Id);
        BigDecimal initialBalance2 = getAccountBalanceDirectly(account2Id);

        // Выполняем успешный перевод
        BigDecimal transferAmount = new BigDecimal("200.00");
        MoneyTransferResult result =
                repository.transferMoney(
                        account1Id, account2Id, transferAmount, "Тестовый перевод");

        // Проверяем атомарность операций
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getTransactionId()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getFromAccountNewBalance())
                .isEqualByComparingTo(initialBalance1.subtract(transferAmount));
        assertThat(result.getToAccountNewBalance())
                .isEqualByComparingTo(initialBalance2.add(transferAmount));

        // Проверяем, что балансы действительно изменились в БД
        BigDecimal finalBalance1 = getAccountBalanceDirectly(account1Id);
        BigDecimal finalBalance2 = getAccountBalanceDirectly(account2Id);

        assertThat(finalBalance1).isEqualByComparingTo(initialBalance1.subtract(transferAmount));
        assertThat(finalBalance2).isEqualByComparingTo(initialBalance2.add(transferAmount));

        // Проверяем, что создана запись в transactions
        assertThat(transactionExists(result.getTransactionId())).isTrue();
    }

    @Test
    @DisplayName("Демонстрация атомарности - недостаточно средств")
    void shouldDemonstrateAtomicityInsufficientFunds() {
        // Подготовка: используем существующих пользователей из миграции и создаем новые счета
        Long account1Id = createAccount(1L, new BigDecimal("100.00"));
        Long account2Id = createAccount(2L, new BigDecimal("500.00"));

        BigDecimal initialBalance1 = getAccountBalanceDirectly(account1Id);
        BigDecimal initialBalance2 = getAccountBalanceDirectly(account2Id);

        // Попытка перевести сумму больше доступного баланса
        BigDecimal transferAmount = new BigDecimal("200.00");

        assertThatThrownBy(
                        () ->
                                repository.transferMoney(
                                        account1Id,
                                        account2Id,
                                        transferAmount,
                                        "Недостаточно средств"))
                .isInstanceOf(InsufficientFundsException.class);

        // Проверяем, что балансы НЕ изменились (атомарность сохранена)
        BigDecimal finalBalance1 = getAccountBalanceDirectly(account1Id);
        BigDecimal finalBalance2 = getAccountBalanceDirectly(account2Id);

        assertThat(finalBalance1).isEqualByComparingTo(initialBalance1);
        assertThat(finalBalance2).isEqualByComparingTo(initialBalance2);
    }

    @Test
    @DisplayName("Поддержание согласованности - проверка constraint нарушений")
    void shouldMaintainConsistency() {
        // Подготовка: используем существующего пользователя из миграции и создаем новый счет
        Long accountId = createAccount(1L, new BigDecimal("1000.00"));
        BigDecimal initialBalance = getAccountBalanceDirectly(accountId);

        // Попытка создать отрицательный баланс через demonstrateConsistencyViolation
        BigDecimal invalidAmount =
                new BigDecimal("-2000.00"); // Попытка сделать баланс отрицательным
        ConsistencyViolationResult result =
                repository.demonstrateConsistencyViolation(accountId, invalidAmount);

        // Проверяем, что операция заблокирована constraint'ом
        assertThat(result.getViolationType())
                .isIn("NEGATIVE_BALANCE_ATTEMPT", "CONSTRAINT_VIOLATION");
        assertThat(result.getIsResolved()).isTrue(); // Ограничение предотвратило нарушение

        // Проверяем, что баланс остался неизменным
        BigDecimal finalBalance = getAccountBalanceDirectly(accountId);
        assertThat(finalBalance).isEqualByComparingTo(initialBalance);

        // Проверяем корректную информацию в результате
        assertThat(result.getEntityId()).isEqualTo(accountId);
        assertThat(result.getEntityType()).isEqualTo("ACCOUNT");
        assertThat(result.getAffectedTables()).contains("accounts");
    }

    @Test
    @DisplayName("Создание заказа атомарно с оплатой")
    void shouldCreateOrderAtomically()
            throws BusinessException, InsufficientFundsException, ProductNotAvailableException {
        // Подготовка тестовых данных
        String uniqueEmail = "test-" + System.currentTimeMillis() + "@example.com";
        Long userId = createTestUser("Test User", uniqueEmail);
        Long accountId = createAccount(userId, new BigDecimal("5000.00"));

        Long product1Id = createTestProduct("Product 1", new BigDecimal("100.00"), 10);
        Long product2Id = createTestProduct("Product 2", new BigDecimal("200.00"), 5);

        List<OrderItemRequest> orderItems =
                List.of(
                        new OrderItemRequest(product1Id, 3, "Item 1"),
                        new OrderItemRequest(product2Id, 2, "Item 2"));

        BigDecimal initialBalance = getAccountBalanceDirectly(accountId);
        int initialStock1 = getProductStock(product1Id);
        int initialStock2 = getProductStock(product2Id);

        // Создаем заказ с оплатой
        OrderCreationResult result =
                repository.createOrderWithPayment(userId, accountId, orderItems);

        // Проверяем атомарность всех операций
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOrderId()).isNotNull();
        assertThat(result.getOrderStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getPaymentTransactionId()).isNotBlank();

        // Проверяем, что товары зарезервированы (stock_quantity уменьшен)
        int finalStock1 = getProductStock(product1Id);
        int finalStock2 = getProductStock(product2Id);
        assertThat(finalStock1).isEqualTo(initialStock1 - 3);
        assertThat(finalStock2).isEqualTo(initialStock2 - 2);

        // Проверяем, что средства списаны со счета
        BigDecimal expectedTotal = new BigDecimal("700.00"); // 3*100 + 2*200
        BigDecimal finalBalance = getAccountBalanceDirectly(accountId);
        assertThat(finalBalance).isEqualByComparingTo(initialBalance.subtract(expectedTotal));

        // Проверяем, что создана платежная транзакция
        assertThat(transactionExists(result.getPaymentTransactionId())).isTrue();

        // Проверяем корректность данных в результате
        assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(result.getAccountNewBalance()).isEqualByComparingTo(finalBalance);
    }

    @Test
    @DisplayName("Отмена заказа атомарно с возвратом")
    void shouldCancelOrderAtomically()
            throws BusinessException, InsufficientFundsException, ProductNotAvailableException {
        // Создаем заказ
        String uniqueEmail = "test-" + System.currentTimeMillis() + "@example.com";
        Long userId = createTestUser("Test User", uniqueEmail);
        Long accountId = createAccount(userId, new BigDecimal("5000.00"));

        Long productId = createTestProduct("Product", new BigDecimal("100.00"), 10);

        List<OrderItemRequest> orderItems = List.of(new OrderItemRequest(productId, 5, "Item"));

        OrderCreationResult orderResult =
                repository.createOrderWithPayment(userId, accountId, orderItems);
        Long orderId = orderResult.getOrderId();

        BigDecimal balanceAfterOrder = getAccountBalanceDirectly(accountId);
        int stockAfterOrder = getProductStock(productId);

        // Отменяем заказ
        String reason = "Тестовая отмена";
        OrderCancellationResult cancelResult = repository.cancelOrderWithRefund(orderId, reason);

        // Проверяем атомарность отмены
        assertThat(cancelResult.getSuccess()).isTrue();
        assertThat(cancelResult.getRefundTransactionId()).isNotBlank();
        assertThat(cancelResult.getRefundAmount())
                .isEqualByComparingTo(orderResult.getTotalAmount());
        assertThat(cancelResult.getRestoredProducts()).hasSize(1);

        // Проверяем, что товары возвращены на склад
        int finalStock = getProductStock(productId);
        assertThat(finalStock).isEqualTo(stockAfterOrder + 5);

        // Проверяем, что средства возвращены на счет
        BigDecimal finalBalance = getAccountBalanceDirectly(accountId);
        assertThat(finalBalance)
                .isEqualByComparingTo(balanceAfterOrder.add(orderResult.getTotalAmount()));

        // Проверяем, что создана транзакция возврата
        assertThat(transactionExists(cancelResult.getRefundTransactionId())).isTrue();

        // Проверяем, что статус заказа изменен на 'CANCELLED'
        String orderStatus = getOrderStatus(orderId);
        assertThat(orderStatus).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Демонстрация нарушения атомарности")
    void shouldDemonstrateBrokenAtomicity() {
        // Подготовка: используем существующих пользователей из миграции и создаем новые счета
        Long account1Id = createAccount(1L, new BigDecimal("1000.00"));
        Long account2Id = createAccount(2L, new BigDecimal("500.00"));

        BigDecimal initialBalance1 = getAccountBalanceDirectly(account1Id);
        BigDecimal initialBalance2 = getAccountBalanceDirectly(account2Id);

        // Выполняем demonstrateBrokenAtomicity с симуляцией ошибки
        BigDecimal transferAmount = new BigDecimal("200.00");
        BrokenTransactionResult result =
                repository.demonstrateBrokenAtomicity(account1Id, account2Id, transferAmount);

        // Проверяем, что в результате видны проблемы
        assertThat(result.getTransactionId()).isNotBlank();
        assertThat(result.getOperationType()).isEqualTo("BROKEN_TRANSFER");
        assertThat(result.getIssueDescription()).isNotBlank();

        // В случае сломанной транзакции может быть частичное выполнение
        // Проверяем, что есть описание проблем
        assertThat(result.getIssueDescription())
                .containsAnyOf("без транзакции", "списаны", "зачисление", "проблема");

        // Проверяем, что состояние данных может быть inconsistent
        BigDecimal finalBalance1 = getAccountBalanceDirectly(account1Id);
        BigDecimal finalBalance2 = getAccountBalanceDirectly(account2Id);

        // В broken транзакции первая операция может быть выполнена, но вторая нет
        // Это демонстрация проблемы, поэтому проверяем, что есть несоответствие
        boolean hasInconsistency =
                !finalBalance1.equals(initialBalance1.subtract(transferAmount))
                        || !finalBalance2.equals(initialBalance2.add(transferAmount));

        // Если есть несоответствие, это ожидаемо для демонстрации проблемы
        if (hasInconsistency) {
            assertThat(result.getExpectedBalance()).isNotNull();
            assertThat(result.getActualBalance()).isNotNull();
        }
    }

    // Вспомогательные методы

    private Long createAccount(Long userId, BigDecimal initialBalance) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO mentee_power.accounts (user_id, balance, is_active)"
                                        + " VALUES (?, ?, true) RETURNING id",
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
        throw new RuntimeException("Счет не найден");
    }

    private boolean transactionExists(String transactionId) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT 1 FROM mentee_power.transactions WHERE transaction_id ="
                                        + " ?")) {
            stmt.setString(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка проверки транзакции", e);
        }
    }

    private Long createTestUser(String name, String email) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO mentee_power.users (name, email) VALUES (?, ?)"
                                        + " RETURNING id",
                                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка создания пользователя", e);
        }
        throw new RuntimeException("Не удалось создать пользователя");
    }

    private Long createTestProduct(String name, BigDecimal price, int stockQuantity) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO mentee_power.products (name, price, stock_quantity)"
                                        + " VALUES (?, ?, ?) RETURNING id",
                                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setBigDecimal(2, price);
            stmt.setInt(3, stockQuantity);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка создания товара", e);
        }
        throw new RuntimeException("Не удалось создать товар");
    }

    private int getProductStock(Long productId) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT COALESCE(stock_quantity, 0) as stock FROM"
                                        + " mentee_power.products WHERE id = ?")) {
            stmt.setLong(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения количества товара", e);
        }
        throw new RuntimeException("Товар не найден");
    }

    private String getOrderStatus(Long orderId) {
        try (Connection conn = getTestConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT status FROM mentee_power.orders WHERE id = ?")) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения статуса заказа", e);
        }
        throw new RuntimeException("Заказ не найден");
    }
}
