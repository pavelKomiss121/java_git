/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.service;

import java.math.BigDecimal;
import java.util.List;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.InsufficientFundsException;
import ru.mentee.power.exception.ProductNotAvailableException;
import ru.mentee.power.model.mp165.*;

/**
 * Service для демонстрации ACID транзакций в бизнес-операциях.
 * Показывает атомарность, согласованность, изолированность и долговечность.
 */
public interface AcidTransactionService {

    /**
     * Выполняет атомарный перевод денег между счетами пользователей.
     * Демонстрирует Atomicity - либо все операции успешны, либо ни одна.
     *
     * @param fromAccountId счет для списания
     * @param toAccountId счет для зачисления
     * @param amount сумма перевода
     * @param description описание транзакции
     * @return результат операции перевода
     * @throws InsufficientFundsException если недостаточно средств
     * @throws BusinessException при других бизнес-ошибках
     */
    MoneyTransferResult transferMoney(
            Long fromAccountId, Long toAccountId, BigDecimal amount, String description)
            throws InsufficientFundsException, BusinessException;

    /**
     * Создает заказ с резервированием товаров и списанием средств.
     * Демонстрирует сложную атомарную операцию с множественными таблицами.
     *
     * @param userId идентификатор пользователя
     * @param accountId счет для оплаты
     * @param orderItems список товаров для заказа
     * @return результат создания заказа
     * @throws InsufficientFundsException если недостаточно средств
     * @throws ProductNotAvailableException если товар недоступен
     * @throws BusinessException при других бизнес-ошибках
     */
    OrderCreationResult createOrderWithPayment(
            Long userId, Long accountId, List<OrderItemRequest> orderItems)
            throws InsufficientFundsException, ProductNotAvailableException, BusinessException;

    /**
     * Отменяет заказ с возвратом товаров на склад и возвратом средств.
     * Демонстрирует компенсирующую транзакцию.
     *
     * @param orderId идентификатор заказа для отмены
     * @param reason причина отмены
     * @return результат отмены заказа
     * @throws BusinessException если заказ нельзя отменить
     */
    OrderCancellationResult cancelOrderWithRefund(Long orderId, String reason)
            throws BusinessException;

    /**
     * Демонстрирует нарушение атомарности (для обучения).
     * Выполняет операции БЕЗ транзакции, показывая проблемы.
     *
     * @param fromAccountId счет для списания
     * @param toAccountId счет для зачисления
     * @param amount сумма перевода
     * @return результат операции с потенциальными проблемами
     */
    BrokenTransactionResult demonstrateBrokenAtomicity(
            Long fromAccountId, Long toAccountId, BigDecimal amount);

    /**
     * Демонстрирует нарушение согласованности.
     * Пытается создать невалидное состояние данных.
     *
     * @param accountId идентификатор счета
     * @param invalidAmount невалидная сумма
     * @return результат с информацией о нарушении ограничений
     */
    ConsistencyViolationResult demonstrateConsistencyViolation(
            Long accountId, BigDecimal invalidAmount);

    /**
     * Получает баланс счета с учетом текущего уровня изоляции транзакций.
     *
     * @param accountId идентификатор счета
     * @return текущий баланс
     * @throws BusinessException при ошибках доступа
     */
    BigDecimal getAccountBalance(Long accountId) throws BusinessException;

    /**
     * Получает детальную историю транзакций для аудита.
     *
     * @param accountId идентификатор счета
     * @param limit максимальное количество записей
     * @return история транзакций
     * @throws BusinessException при ошибках доступа
     */
    List<TransactionHistory> getTransactionHistory(Long accountId, Integer limit)
            throws BusinessException;
}
