/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.interfaces;

import java.math.BigDecimal;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp167.ConcurrencyAnomalyResult;
import ru.mentee.power.model.mp167.MoneyTransferResult;

/**
 * Repository для демонстрации проблем конкурентности в базе данных.
 * Воспроизводит Dirty Read, Non-repeatable Read, Phantom Read через JDBC.
 */
public interface ConcurrencyProblemsRepository {

    /**
     * Демонстрирует аномалию Dirty Read на уровне изоляции READ UNCOMMITTED.
     *
     * @param accountId идентификатор банковского счета для тестирования
     * @param amountToChange сумма изменения для незафиксированной транзакции
     * @return результат демонстрации с детальной информацией об аномалии
     * @throws DataAccessException при ошибках доступа к базе данных
     */
    ConcurrencyAnomalyResult demonstrateDirtyRead(Long accountId, BigDecimal amountToChange)
            throws DataAccessException;

    /**
     * Демонстрирует аномалию Non-repeatable Read на уровне изоляции READ COMMITTED.
     *
     * @param accountId идентификатор банковского счета для тестирования
     * @param amountToChange сумма изменения между чтениями в транзакции
     * @return результат демонстрации с информацией о неповторяющемся чтении
     * @throws DataAccessException при ошибках доступа к базе данных
     */
    ConcurrencyAnomalyResult demonstrateNonRepeatableRead(Long accountId, BigDecimal amountToChange)
            throws DataAccessException;

    /**
     * Демонстрирует аномалию Phantom Read на примере подсчета транзакций.
     *
     * @param accountId идентификатор банковского счета
     * @param thresholdAmount пороговая сумма для фильтрации транзакций
     * @param newTransactionAmount сумма новой транзакции для создания phantom read
     * @return результат демонстрации фантомного чтения
     * @throws DataAccessException при ошибках доступа к базе данных
     */
    ConcurrencyAnomalyResult demonstratePhantomRead(
            Long accountId, BigDecimal thresholdAmount, BigDecimal newTransactionAmount)
            throws DataAccessException;

    /**
     * Демонстрирует проблему Lost Update при конкурентных изменениях.
     *
     * @param accountId идентификатор банковского счета
     * @param firstAmount сумма первой одновременной транзакции
     * @param secondAmount сумма второй одновременной транзакции
     * @return результат демонстрации потерянного обновления
     * @throws DataAccessException при ошибках доступа к базе данных
     */
    ConcurrencyAnomalyResult demonstrateLostUpdate(
            Long accountId, BigDecimal firstAmount, BigDecimal secondAmount)
            throws DataAccessException;

    /**
     * Выполняет безопасный перевод денег с правильным уровнем изоляции.
     *
     * @param fromAccountId идентификатор счета отправителя
     * @param toAccountId идентификатор счета получателя
     * @param amount сумма перевода
     * @return результат безопасной операции перевода
     * @throws DataAccessException при ошибках доступа к базе данных
     */
    MoneyTransferResult safeMoneyTransfer(Long fromAccountId, Long toAccountId, BigDecimal amount)
            throws DataAccessException;

    /**
     * Получает информацию о текущих настройках изоляции PostgreSQL.
     *
     * @return строка с информацией об уровне изоляции и конфигурации
     * @throws DataAccessException при ошибках запроса настроек
     */
    String getCurrentIsolationLevelInfo() throws DataAccessException;
}
