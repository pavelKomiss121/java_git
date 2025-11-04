/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.InsufficientFundsException;
import ru.mentee.power.exception.ProductNotAvailableException;
import ru.mentee.power.model.mp165.*;
import ru.mentee.power.repository.interfaces.AcidTransactionRepository;
import ru.mentee.power.service.AcidTransactionService;

public class PostgresAcidTransactionRepository
        implements AcidTransactionRepository, AcidTransactionService {

    // SQL константы для атомарных операций

    private static final String GET_ACCOUNT_BALANCE =
            """
            SELECT balance, is_active
            FROM mentee_power.accounts
            WHERE id = ? AND is_active = true;
            """;

    private static final String UPDATE_ACCOUNT_BALANCE =
            """
            UPDATE mentee_power.accounts
            SET balance = balance + ?
            WHERE id = ? AND is_active = true
            RETURNING balance;
            """;

    private static final String INSERT_TRANSACTION =
            """
            INSERT INTO mentee_power.transactions
                (transaction_id, from_account_id, to_account_id, amount, description, status, transaction_type, processed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            RETURNING id;
            """;

    private static final String GET_PRODUCT_INFO =
            """
            SELECT id, name, sku, price, COALESCE(stock_quantity, 0) as stock_quantity
            FROM mentee_power.products
            WHERE id = ?;
            """;

    private static final String UPDATE_PRODUCT_STOCK =
            """
            UPDATE mentee_power.products
            SET stock_quantity = stock_quantity + ?
            WHERE id = ? AND stock_quantity + ? >= 0
            RETURNING stock_quantity;
            """;

    private static final String INSERT_ORDER =
            """
            INSERT INTO mentee_power.orders (user_id, total, status, created_at)
            VALUES (?, ?, 'PENDING', NOW())
            RETURNING id, created_at;
            """;

    private static final String INSERT_ORDER_ITEM =
            """
            INSERT INTO mentee_power.order_items (order_id, product_id, quantity, price)
            VALUES (?, ?, ?, ?)
            RETURNING id;
            """;

    private static final String UPDATE_ORDER_STATUS =
            """
            UPDATE mentee_power.orders
            SET status = ?
            WHERE id = ?;
            """;

    private static final String GET_ORDER_INFO =
            """
            SELECT id, user_id, total, status, created_at
            FROM mentee_power.orders
            WHERE id = ?;
            """;

    private static final String GET_ORDER_ITEMS =
            """
            SELECT oi.product_id, p.name as product_name, p.sku as product_sku,
                   oi.quantity, oi.price, COALESCE(p.stock_quantity, 0) as stock_quantity
            FROM mentee_power.order_items oi
            JOIN mentee_power.products p ON p.id = oi.product_id
            WHERE oi.order_id = ?;
            """;

    private static final String GET_TRANSACTION_HISTORY =
            """
            SELECT
                transaction_id,
                transaction_type,
                from_account_id,
                to_account_id,
                amount,
                description,
                status,
                processed_at,
                error_message
            FROM mentee_power.transactions
            WHERE (from_account_id = ? OR to_account_id = ?)
            ORDER BY processed_at DESC
            LIMIT ?;
            """;

    private ApplicationConfig config;

    public PostgresAcidTransactionRepository(ApplicationConfig config) {
        this.config = config;
    }

    protected Connection getConnection() throws DataAccessException, SQLException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (PreparedStatement statement =
                conn.prepareStatement("SET search_path TO mentee_power, public")) {
            statement.execute();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка соединения", e);
        }
        return conn;
    }

    @Override
    public MoneyTransferResult executeAtomicMoneyTransfer(
            Long fromAccountId, Long toAccountId, BigDecimal amount, String description)
            throws DataAccessException {
        String transactionId = UUID.randomUUID().toString();
        LocalDateTime processedAt = LocalDateTime.now();
        List<String> validationErrors = new ArrayList<>();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                BigDecimal fromBalance = getAccountBalanceInternal(conn, fromAccountId);
                BigDecimal toBalance = getAccountBalanceInternal(conn, toAccountId);

                if (fromBalance == null) {
                    validationErrors.add("Счет отправителя не найден или неактивен");
                }
                if (toBalance == null) {
                    validationErrors.add("Счет получателя не найден или неактивен");
                }
                if (fromAccountId != null && fromAccountId.equals(toAccountId)) {
                    validationErrors.add("Нельзя переводить средства на тот же счет");
                }
                if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
                    validationErrors.add("Сумма перевода должна быть больше нуля");
                }

                if (!validationErrors.isEmpty()) {
                    conn.rollback();
                    return MoneyTransferResult.builder()
                            .success(false)
                            .transactionId(transactionId)
                            .fromAccountId(fromAccountId)
                            .toAccountId(toAccountId)
                            .amount(amount)
                            .description(description)
                            .processedAt(processedAt)
                            .status("FAILED")
                            .errorMessage("Ошибка валидации")
                            .validationErrors(validationErrors)
                            .build();
                }

                if (fromBalance.compareTo(amount) < 0) {
                    conn.rollback();
                    return MoneyTransferResult.builder()
                            .success(false)
                            .transactionId(transactionId)
                            .fromAccountId(fromAccountId)
                            .toAccountId(toAccountId)
                            .amount(amount)
                            .description(description)
                            .processedAt(processedAt)
                            .status("FAILED")
                            .errorMessage("Недостаточно средств на счете")
                            .validationErrors(
                                    List.of(
                                            "Недостаточно средств: баланс "
                                                    + fromBalance
                                                    + ", требуется "
                                                    + amount))
                            .build();
                }

                BigDecimal fromAccountNewBalance =
                        updateAccountBalance(conn, fromAccountId, amount.negate());
                BigDecimal toAccountNewBalance = updateAccountBalance(conn, toAccountId, amount);

                try (PreparedStatement insertStmt = conn.prepareStatement(INSERT_TRANSACTION)) {
                    insertStmt.setString(1, transactionId);
                    insertStmt.setLong(2, fromAccountId);
                    insertStmt.setLong(3, toAccountId);
                    insertStmt.setBigDecimal(4, amount);
                    insertStmt.setString(5, description);
                    insertStmt.setString(6, "SUCCESS");
                    insertStmt.setString(7, "MONEY_TRANSFER");
                    insertStmt.execute();
                }

                conn.commit();

                return MoneyTransferResult.builder()
                        .success(true)
                        .transactionId(transactionId)
                        .fromAccountId(fromAccountId)
                        .toAccountId(toAccountId)
                        .amount(amount)
                        .fromAccountNewBalance(fromAccountNewBalance)
                        .toAccountNewBalance(toAccountNewBalance)
                        .description(description)
                        .processedAt(processedAt)
                        .status("SUCCESS")
                        .validationErrors(List.of())
                        .build();

            } catch (SQLException e) {
                conn.rollback();
                return MoneyTransferResult.builder()
                        .success(false)
                        .transactionId(transactionId)
                        .fromAccountId(fromAccountId)
                        .toAccountId(toAccountId)
                        .amount(amount)
                        .description(description)
                        .processedAt(processedAt)
                        .status("FAILED")
                        .errorMessage("Ошибка выполнения транзакции: " + e.getMessage())
                        .validationErrors(List.of())
                        .build();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка выполнения атомарного перевода денег", ex);
        }
    }

    @Override
    public OrderCreationResult createOrderAtomically(
            Long userId, Long accountId, List<OrderItemRequest> orderItems)
            throws DataAccessException {
        LocalDateTime createdAt = LocalDateTime.now();
        List<String> validationErrors = new ArrayList<>();
        String paymentTransactionId = UUID.randomUUID().toString();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                if (orderItems == null || orderItems.isEmpty()) {
                    validationErrors.add("Список товаров не может быть пустым");
                }
                if (accountId == null) {
                    validationErrors.add("Счет не указан");
                }

                if (!validationErrors.isEmpty()) {
                    conn.rollback();
                    return OrderCreationResult.builder()
                            .success(false)
                            .userId(userId)
                            .createdAt(createdAt)
                            .errorMessage("Ошибка валидации")
                            .validationErrors(validationErrors)
                            .build();
                }

                BigDecimal totalAmount = BigDecimal.ZERO;
                List<OrderItemResult> items = new ArrayList<>();

                for (OrderItemRequest itemRequest : orderItems) {
                    ProductInfo productInfo = getProductInfo(conn, itemRequest.getProductId());
                    if (productInfo == null) {
                        validationErrors.add(
                                "Товар с ID " + itemRequest.getProductId() + " не найден");
                        continue;
                    }

                    if (productInfo.stockQuantity < itemRequest.getQuantity()) {
                        validationErrors.add(
                                "Недостаточно товара "
                                        + productInfo.name
                                        + ": на складе "
                                        + productInfo.stockQuantity
                                        + ", требуется "
                                        + itemRequest.getQuantity());
                        continue;
                    }

                    BigDecimal itemTotal =
                            productInfo.price.multiply(new BigDecimal(itemRequest.getQuantity()));
                    totalAmount = totalAmount.add(itemTotal);

                    items.add(
                            OrderItemResult.builder()
                                    .productId(productInfo.id)
                                    .productName(productInfo.name)
                                    .productSku(productInfo.sku)
                                    .quantityOrdered(itemRequest.getQuantity())
                                    .quantityReserved(itemRequest.getQuantity())
                                    .unitPrice(productInfo.price)
                                    .totalPrice(itemTotal)
                                    .status("RESERVED")
                                    .build());
                }

                if (!validationErrors.isEmpty()) {
                    conn.rollback();
                    return OrderCreationResult.builder()
                            .success(false)
                            .userId(userId)
                            .totalAmount(totalAmount)
                            .createdAt(createdAt)
                            .errorMessage("Ошибка валидации товаров")
                            .validationErrors(validationErrors)
                            .build();
                }

                // 2. Проверка баланса счета
                BigDecimal accountBalance = getAccountBalanceInternal(conn, accountId);
                if (accountBalance == null) {
                    conn.rollback();
                    return OrderCreationResult.builder()
                            .success(false)
                            .userId(userId)
                            .totalAmount(totalAmount)
                            .createdAt(createdAt)
                            .errorMessage("Счет не найден или неактивен")
                            .validationErrors(
                                    List.of("Счет с ID " + accountId + " не найден или неактивен"))
                            .build();
                }

                if (accountBalance.compareTo(totalAmount) < 0) {
                    conn.rollback();
                    return OrderCreationResult.builder()
                            .success(false)
                            .userId(userId)
                            .totalAmount(totalAmount)
                            .createdAt(createdAt)
                            .errorMessage("Недостаточно средств на счете")
                            .validationErrors(
                                    List.of(
                                            "Баланс: "
                                                    + accountBalance
                                                    + ", требуется: "
                                                    + totalAmount))
                            .build();
                }

                // 3. Создание записи заказа в статусе 'PENDING'
                Long orderId;
                try (PreparedStatement orderStmt = conn.prepareStatement(INSERT_ORDER)) {
                    orderStmt.setLong(1, userId);
                    orderStmt.setBigDecimal(2, totalAmount);
                    try (ResultSet rs = orderStmt.executeQuery()) {
                        if (rs.next()) {
                            orderId = rs.getLong("id");
                            createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                        } else {
                            throw new SQLException("Не удалось создать заказ");
                        }
                    }
                }

                // 4. Зарезервировать товары (уменьшить stock_quantity) и создать order_items
                for (int i = 0; i < orderItems.size(); i++) {
                    OrderItemRequest itemRequest = orderItems.get(i);
                    OrderItemResult itemResult = items.get(i);

                    Integer newStockQuantity =
                            updateProductStock(
                                    conn, itemRequest.getProductId(), -itemRequest.getQuantity());
                    itemResult.setNewStockQuantity(newStockQuantity);

                    try (PreparedStatement itemStmt = conn.prepareStatement(INSERT_ORDER_ITEM)) {
                        itemStmt.setLong(1, orderId);
                        itemStmt.setLong(2, itemRequest.getProductId());
                        itemStmt.setInt(3, itemRequest.getQuantity());
                        itemStmt.setBigDecimal(4, itemResult.getUnitPrice());
                        itemStmt.execute();
                    }
                }

                // 5. Списать средства со счета пользователя
                BigDecimal accountNewBalance =
                        updateAccountBalance(conn, accountId, totalAmount.negate());

                // 6. Создать запись о платеже в transactions
                try (PreparedStatement transStmt = conn.prepareStatement(INSERT_TRANSACTION)) {
                    transStmt.setString(1, paymentTransactionId);
                    transStmt.setLong(2, accountId);
                    transStmt.setObject(3, null);
                    transStmt.setBigDecimal(4, totalAmount);
                    transStmt.setString(5, "Оплата заказа #" + orderId);
                    transStmt.setString(6, "SUCCESS");
                    transStmt.setString(7, "ORDER_PAYMENT");
                    transStmt.execute();
                }

                // 7. Обновить статус заказа на 'CONFIRMED'
                try (PreparedStatement updateStmt = conn.prepareStatement(UPDATE_ORDER_STATUS)) {
                    updateStmt.setString(1, "CONFIRMED");
                    updateStmt.setLong(2, orderId);
                    updateStmt.execute();
                }

                conn.commit();

                return OrderCreationResult.builder()
                        .success(true)
                        .orderId(orderId)
                        .userId(userId)
                        .totalAmount(totalAmount)
                        .items(items)
                        .paymentTransactionId(paymentTransactionId)
                        .accountNewBalance(accountNewBalance)
                        .orderStatus("CONFIRMED")
                        .createdAt(createdAt)
                        .validationErrors(List.of())
                        .build();

            } catch (SQLException e) {
                conn.rollback();
                return OrderCreationResult.builder()
                        .success(false)
                        .userId(userId)
                        .createdAt(createdAt)
                        .errorMessage("Ошибка создания заказа: " + e.getMessage())
                        .validationErrors(List.of())
                        .build();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка создания заказа атомарно", ex);
        }
    }

    @Override
    public OrderCancellationResult cancelOrderAtomically(Long orderId, String reason)
            throws DataAccessException {
        LocalDateTime cancelledAt = LocalDateTime.now();
        String refundTransactionId = UUID.randomUUID().toString();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                OrderInfo orderInfo = getOrderInfo(conn, orderId);
                if (orderInfo == null) {
                    conn.rollback();
                    return OrderCancellationResult.builder()
                            .success(false)
                            .orderId(orderId)
                            .cancelledAt(cancelledAt)
                            .errorMessage("Заказ не найден")
                            .build();
                }

                if ("CANCELLED".equals(orderInfo.status)) {
                    conn.rollback();
                    return OrderCancellationResult.builder()
                            .success(false)
                            .orderId(orderId)
                            .cancelledAt(cancelledAt)
                            .errorMessage("Заказ уже отменен")
                            .build();
                }

                List<OrderItemInfo> orderItems = getOrderItems(conn, orderId);

                List<ProductRestoreResult> restoredProducts = new ArrayList<>();
                for (OrderItemInfo item : orderItems) {
                    Integer newStockQuantity =
                            updateProductStock(conn, item.productId, item.quantity);
                    restoredProducts.add(
                            ProductRestoreResult.builder()
                                    .productId(item.productId)
                                    .productName(item.productName)
                                    .productSku(item.productSku)
                                    .quantityRestored(item.quantity)
                                    .newStockQuantity(newStockQuantity)
                                    .status("RESTORED")
                                    .build());
                }

                Long accountId = getAccountIdByUserId(conn, orderInfo.userId);
                if (accountId == null) {
                    conn.rollback();
                    return OrderCancellationResult.builder()
                            .success(false)
                            .orderId(orderId)
                            .cancelledAt(cancelledAt)
                            .errorMessage("Счет пользователя не найден")
                            .build();
                }

                BigDecimal accountNewBalance =
                        updateAccountBalance(conn, accountId, orderInfo.total);

                try (PreparedStatement transStmt = conn.prepareStatement(INSERT_TRANSACTION)) {
                    transStmt.setString(1, refundTransactionId);
                    transStmt.setObject(2, null);
                    transStmt.setLong(3, accountId);
                    transStmt.setBigDecimal(4, orderInfo.total);
                    transStmt.setString(5, "Возврат средств за отмененный заказ #" + orderId);
                    transStmt.setString(6, "SUCCESS");
                    transStmt.setString(7, "ORDER_REFUND");
                    transStmt.execute();
                }

                try (PreparedStatement updateStmt = conn.prepareStatement(UPDATE_ORDER_STATUS)) {
                    updateStmt.setString(1, "CANCELLED");
                    updateStmt.setLong(2, orderId);
                    updateStmt.execute();
                }

                conn.commit();

                return OrderCancellationResult.builder()
                        .success(true)
                        .orderId(orderId)
                        .refundTransactionId(refundTransactionId)
                        .refundAmount(orderInfo.total)
                        .restoredProducts(restoredProducts)
                        .accountNewBalance(accountNewBalance)
                        .reason(reason)
                        .cancelledAt(cancelledAt)
                        .build();

            } catch (SQLException e) {
                conn.rollback();
                return OrderCancellationResult.builder()
                        .success(false)
                        .orderId(orderId)
                        .cancelledAt(cancelledAt)
                        .errorMessage("Ошибка отмены заказа: " + e.getMessage())
                        .build();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка отмены заказа атомарно", ex);
        }
    }

    @Override
    public BrokenTransactionResult executeBrokenTransfer(
            Long fromAccountId, Long toAccountId, BigDecimal amount) throws DataAccessException {
        LocalDateTime occurredAt = LocalDateTime.now();
        BigDecimal expectedBalance = null;
        BigDecimal actualBalance = null;

        try (Connection conn = getConnection()) {

            BigDecimal fromBalanceBefore = getAccountBalanceInternal(conn, fromAccountId);
            BigDecimal toBalanceBefore = getAccountBalanceInternal(conn, toAccountId);

            if (fromBalanceBefore == null || toBalanceBefore == null) {
                return BrokenTransactionResult.builder()
                        .transactionId("BROKEN-" + UUID.randomUUID().toString())
                        .operationType("BROKEN_TRANSFER")
                        .accountId(fromAccountId)
                        .amount(amount)
                        .expectedBalance(null)
                        .actualBalance(null)
                        .issueDescription("Один из счетов не найден")
                        .occurredAt(occurredAt)
                        .isRolledBack(false)
                        .build();
            }

            BigDecimal fromBalanceAfter =
                    updateAccountBalance(conn, fromAccountId, amount.negate());
            expectedBalance = fromBalanceBefore.subtract(amount);

            try {
                updateAccountBalance(conn, toAccountId, amount);
                actualBalance = fromBalanceAfter;
            } catch (SQLException e) {
                actualBalance = getAccountBalanceInternal(conn, fromAccountId);
                return BrokenTransactionResult.builder()
                        .transactionId("BROKEN-" + UUID.randomUUID().toString())
                        .operationType("BROKEN_TRANSFER")
                        .accountId(fromAccountId)
                        .amount(amount)
                        .expectedBalance(expectedBalance)
                        .actualBalance(actualBalance)
                        .issueDescription(
                                "Деньги списаны, но зачисление не выполнено: " + e.getMessage())
                        .occurredAt(occurredAt)
                        .isRolledBack(false)
                        .build();
            }

            return BrokenTransactionResult.builder()
                    .transactionId("BROKEN-" + UUID.randomUUID().toString())
                    .operationType("BROKEN_TRANSFER")
                    .accountId(fromAccountId)
                    .amount(amount)
                    .expectedBalance(expectedBalance)
                    .actualBalance(actualBalance)
                    .issueDescription("Демонстрация проблемы: операции выполнены без транзакции")
                    .occurredAt(occurredAt)
                    .isRolledBack(false)
                    .build();

        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка выполнения сломанного перевода", ex);
        }
    }

    @Override
    public MoneyTransferResult transferMoney(
            Long fromAccountId, Long toAccountId, BigDecimal amount, String description)
            throws InsufficientFundsException, BusinessException {
        try {
            MoneyTransferResult result =
                    executeAtomicMoneyTransfer(fromAccountId, toAccountId, amount, description);
            if (!result.getSuccess()) {
                if (result.getErrorMessage() != null
                        && result.getErrorMessage().contains("Недостаточно средств")) {
                    throw new InsufficientFundsException(
                            "Недостаточно средств на счете " + fromAccountId);
                }
                throw new BusinessException(
                        result.getErrorMessage() != null
                                ? result.getErrorMessage()
                                : "Ошибка перевода");
            }
            return result;
        } catch (DataAccessException e) {
            throw new BusinessException(
                    "Ошибка доступа к данным при переводе: " + e.getMessage(), e);
        }
    }

    @Override
    public OrderCreationResult createOrderWithPayment(
            Long userId, Long accountId, List<OrderItemRequest> orderItems)
            throws InsufficientFundsException, ProductNotAvailableException, BusinessException {
        try {
            OrderCreationResult result = createOrderAtomically(userId, accountId, orderItems);
            if (!result.getSuccess()) {
                if (result.getErrorMessage() != null
                        && result.getErrorMessage().contains("Недостаточно средств")) {
                    throw new InsufficientFundsException(
                            "Недостаточно средств на счете " + accountId);
                }
                if (result.getValidationErrors() != null
                        && result.getValidationErrors().stream()
                                .anyMatch(
                                        e ->
                                                e.contains("Недостаточно товара")
                                                        || e.contains("не найден"))) {
                    throw new ProductNotAvailableException(
                            "Товар недоступен: " + result.getValidationErrors());
                }
                throw new BusinessException(
                        result.getErrorMessage() != null
                                ? result.getErrorMessage()
                                : "Ошибка создания заказа");
            }
            return result;
        } catch (DataAccessException e) {
            throw new BusinessException(
                    "Ошибка доступа к данным при создании заказа: " + e.getMessage(), e);
        }
    }

    @Override
    public OrderCancellationResult cancelOrderWithRefund(Long orderId, String reason)
            throws BusinessException {
        try {
            OrderCancellationResult result = cancelOrderAtomically(orderId, reason);
            if (!result.getSuccess()) {
                throw new BusinessException(
                        result.getErrorMessage() != null
                                ? result.getErrorMessage()
                                : "Ошибка отмены заказа");
            }
            return result;
        } catch (DataAccessException e) {
            throw new BusinessException(
                    "Ошибка доступа к данным при отмене заказа: " + e.getMessage(), e);
        }
    }

    @Override
    public BrokenTransactionResult demonstrateBrokenAtomicity(
            Long fromAccountId, Long toAccountId, BigDecimal amount) {
        try {
            return executeBrokenTransfer(fromAccountId, toAccountId, amount);
        } catch (DataAccessException e) {
            return BrokenTransactionResult.builder()
                    .transactionId("BROKEN-" + UUID.randomUUID().toString())
                    .operationType("BROKEN_TRANSFER")
                    .accountId(fromAccountId)
                    .amount(amount)
                    .issueDescription("Ошибка при демонстрации: " + e.getMessage())
                    .occurredAt(LocalDateTime.now())
                    .isRolledBack(false)
                    .build();
        }
    }

    @Override
    public ConsistencyViolationResult demonstrateConsistencyViolation(
            Long accountId, BigDecimal invalidAmount) {
        LocalDateTime detectedAt = LocalDateTime.now();

        try (Connection conn = getConnection()) {
            BigDecimal currentBalance = getAccountBalanceInternal(conn, accountId);
            if (currentBalance == null) {
                return ConsistencyViolationResult.builder()
                        .violationType("ACCOUNT_NOT_FOUND")
                        .entityId(accountId)
                        .entityType("ACCOUNT")
                        .description("Счет не найден")
                        .detectedAt(detectedAt)
                        .affectedTables("accounts")
                        .isResolved(false)
                        .build();
            }

            try {
                conn.setAutoCommit(false);
                updateAccountBalance(conn, accountId, invalidAmount);
                conn.commit();
            } catch (SQLException e) {
                // Ожидаем ошибку ограничения
                conn.rollback();
                return ConsistencyViolationResult.builder()
                        .violationType("NEGATIVE_BALANCE_ATTEMPT")
                        .entityId(accountId)
                        .entityType("ACCOUNT")
                        .expectedValue(currentBalance.add(invalidAmount))
                        .actualValue(currentBalance)
                        .description("Попытка установить отрицательный баланс: " + e.getMessage())
                        .detectedAt(detectedAt)
                        .affectedTables("accounts")
                        .isResolved(true) // Ограничение предотвратило нарушение
                        .build();
            } finally {
                conn.setAutoCommit(true);
            }

            // Если дошли сюда, ограничение не сработало (что странно)
            return ConsistencyViolationResult.builder()
                    .violationType("CONSTRAINT_VIOLATION")
                    .entityId(accountId)
                    .entityType("ACCOUNT")
                    .expectedValue(currentBalance.add(invalidAmount))
                    .actualValue(getAccountBalanceInternal(conn, accountId))
                    .description("Нарушение ограничения согласованности данных")
                    .detectedAt(detectedAt)
                    .affectedTables("accounts")
                    .isResolved(false)
                    .build();

        } catch (SQLException | DataAccessException e) {
            return ConsistencyViolationResult.builder()
                    .violationType("ERROR")
                    .entityId(accountId)
                    .entityType("ACCOUNT")
                    .description("Ошибка при демонстрации: " + e.getMessage())
                    .detectedAt(detectedAt)
                    .affectedTables("accounts")
                    .isResolved(false)
                    .build();
        }
    }

    // Реализация AcidTransactionService.getAccountBalance - выбрасывает BusinessException
    public BigDecimal getAccountBalance(Long accountId) throws BusinessException {
        try (Connection conn = getConnection()) {
            BigDecimal balance = getAccountBalanceInternal(conn, accountId);
            if (balance == null) {
                throw new BusinessException("Счет с ID " + accountId + " не найден или неактивен");
            }
            return balance;
        } catch (SQLException e) {
            throw new BusinessException("Ошибка получения баланса счета: " + e.getMessage(), e);
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка соединения с базой данных: " + e.getMessage(), e);
        }
    }

    // Реализация AcidTransactionService.getTransactionHistory - выбрасывает BusinessException
    public List<TransactionHistory> getTransactionHistory(Long accountId, Integer limit)
            throws BusinessException {
        List<TransactionHistory> result = new ArrayList<>();

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(GET_TRANSACTION_HISTORY)) {
            statement.setLong(1, accountId);
            statement.setLong(2, accountId);
            statement.setInt(3, limit != null ? limit : 100);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TransactionHistory history =
                            TransactionHistory.builder()
                                    .transactionId(resultSet.getString("transaction_id"))
                                    .transactionType(resultSet.getString("transaction_type"))
                                    .fromAccountId(getLongOrNull(resultSet, "from_account_id"))
                                    .toAccountId(getLongOrNull(resultSet, "to_account_id"))
                                    .amount(resultSet.getBigDecimal("amount"))
                                    .fromAccountBalanceBefore(null)
                                    .fromAccountBalanceAfter(null)
                                    .toAccountBalanceBefore(null)
                                    .toAccountBalanceAfter(null)
                                    .description(resultSet.getString("description"))
                                    .status(resultSet.getString("status"))
                                    .processedAt(
                                            resultSet.getTimestamp("processed_at") != null
                                                    ? resultSet
                                                            .getTimestamp("processed_at")
                                                            .toLocalDateTime()
                                                    : null)
                                    .errorMessage(resultSet.getString("error_message"))
                                    .build();
                    result.add(history);
                }
            }
        } catch (SQLException e) {
            throw new BusinessException(
                    "Ошибка получения истории транзакций: " + e.getMessage(), e);
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка соединения с базой данных: " + e.getMessage(), e);
        }
        return result;
    }

    // Вспомогательные методы

    private BigDecimal getAccountBalanceInternal(Connection conn, Long accountId)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(GET_ACCOUNT_BALANCE)) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }
        return null;
    }

    private BigDecimal updateAccountBalance(Connection conn, Long accountId, BigDecimal amount)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_ACCOUNT_BALANCE)) {
            stmt.setBigDecimal(1, amount);
            stmt.setLong(2, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }
        throw new SQLException("Не удалось обновить баланс счета " + accountId);
    }

    private ProductInfo getProductInfo(Connection conn, Long productId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(GET_PRODUCT_INFO)) {
            stmt.setLong(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ProductInfo(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("sku"),
                            rs.getBigDecimal("price"),
                            rs.getInt("stock_quantity"));
                }
            }
        }
        return null;
    }

    private Integer updateProductStock(Connection conn, Long productId, Integer quantityChange)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_PRODUCT_STOCK)) {
            stmt.setInt(1, quantityChange);
            stmt.setLong(2, productId);
            stmt.setInt(3, quantityChange);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock_quantity");
                }
            }
        }
        throw new SQLException("Не удалось обновить количество товара " + productId);
    }

    private OrderInfo getOrderInfo(Connection conn, Long orderId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(GET_ORDER_INFO)) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new OrderInfo(
                            rs.getLong("id"),
                            rs.getLong("user_id"),
                            rs.getBigDecimal("total"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at") != null
                                    ? rs.getTimestamp("created_at").toLocalDateTime()
                                    : null);
                }
            }
        }
        return null;
    }

    private List<OrderItemInfo> getOrderItems(Connection conn, Long orderId) throws SQLException {
        List<OrderItemInfo> items = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(GET_ORDER_ITEMS)) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(
                            new OrderItemInfo(
                                    rs.getLong("product_id"),
                                    rs.getString("product_name"),
                                    rs.getString("product_sku"),
                                    rs.getInt("quantity"),
                                    rs.getBigDecimal("price"),
                                    rs.getInt("stock_quantity")));
                }
            }
        }
        return items;
    }

    private Long getAccountIdByUserId(Connection conn, Long userId) throws SQLException {
        try (PreparedStatement stmt =
                conn.prepareStatement(
                        "SELECT id FROM mentee_power.accounts WHERE user_id = ? AND is_active ="
                                + " true LIMIT 1")) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return null;
    }

    private Long getLongOrNull(ResultSet rs, String column) throws SQLException {
        Long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    // Внутренние классы для временного хранения данных

    private static class ProductInfo {
        final Long id;
        final String name;
        final String sku;
        final BigDecimal price;
        final Integer stockQuantity;

        ProductInfo(Long id, String name, String sku, BigDecimal price, Integer stockQuantity) {
            this.id = id;
            this.name = name;
            this.sku = sku;
            this.price = price;
            this.stockQuantity = stockQuantity;
        }
    }

    private static class OrderInfo {
        final Long id;
        final Long userId;
        final BigDecimal total;
        final String status;
        final LocalDateTime createdAt;

        OrderInfo(Long id, Long userId, BigDecimal total, String status, LocalDateTime createdAt) {
            this.id = id;
            this.userId = userId;
            this.total = total;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    private static class OrderItemInfo {
        final Long productId;
        final String productName;
        final String productSku;
        final Integer quantity;
        final BigDecimal price;
        final Integer stockQuantity;

        OrderItemInfo(
                Long productId,
                String productName,
                String productSku,
                Integer quantity,
                BigDecimal price,
                Integer stockQuantity) {
            this.productId = productId;
            this.productName = productName;
            this.productSku = productSku;
            this.quantity = quantity;
            this.price = price;
            this.stockQuantity = stockQuantity;
        }
    }
}
