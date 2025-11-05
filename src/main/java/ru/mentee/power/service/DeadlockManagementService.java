/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.service;

import java.math.BigDecimal;
import java.util.List;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.DeadlockException;
import ru.mentee.power.exception.LockTimeoutException;
import ru.mentee.power.model.mp168.*;

/**
 * Service для демонстрации и управления блокировками в PostgreSQL.
 * Показывает методы предотвращения дедлоков и recovery стратегии.
 */
public interface DeadlockManagementService {

    /**
     * Выполняет безопасный трансфер денег с предотвращением дедлоков.
     * Использует упорядочивание ресурсов и retry логику.
     *
     * @param fromAccountId счет списания
     * @param toAccountId счет зачисления
     * @param amount сумма трансфера
     * @param maxRetries максимальное количество попыток при дедлоке
     * @return результат трансфера с информацией о попытках
     * @throws DeadlockException если не удалось выполнить после всех попыток
     * @throws LockTimeoutException если превышен timeout ожидания блокировки
     * @throws BusinessException при других бизнес-ошибках
     */
    SafeTransferResult performSafeTransfer(
            Long fromAccountId, Long toAccountId, BigDecimal amount, Integer maxRetries)
            throws DeadlockException, LockTimeoutException, BusinessException;

    /**
     * Демонстрирует классический дедлок между двумя транзакциями.
     * Используется для обучения и анализа причин дедлоков.
     *
     * @param account1Id первый счет
     * @param account2Id второй счет
     * @param amount1 сумма первого трансфера
     * @param amount2 сумма второго трансфера
     * @return результат демонстрации с деталями дедлока
     * @throws BusinessException при ошибках демонстрации
     */
    DeadlockDemonstrationResult demonstrateClassicDeadlock(
            Long account1Id, Long account2Id, BigDecimal amount1, BigDecimal amount2)
            throws BusinessException;

    /**
     * Выполняет массовое резервирование товаров с обнаружением конфликтов.
     * Демонстрирует inventory locking patterns.
     *
     * @param reservations список резервирований для выполнения
     * @param lockTimeout таймаут ожидания блокировки в секундах
     * @return результат массового резервирования
     * @throws LockTimeoutException если превышен timeout
     * @throws BusinessException при ошибках резервирования
     */
    BulkReservationResult performBulkInventoryReservation(
            List<InventoryReservationRequest> reservations, Integer lockTimeout)
            throws LockTimeoutException, BusinessException;

    /**
     * Мониторит текущие блокировки в системе.
     * Предоставляет real-time информацию о заблокированных транзакциях.
     *
     * @return список текущих блокировок с деталями
     * @throws BusinessException при ошибках мониторинга
     */
    List<LockMonitoringInfo> getCurrentLockStatus() throws BusinessException;

    /**
     * Анализирует историю дедлоков для выявления паттернов.
     * Помогает в оптимизации логики предотвращения дедлоков.
     *
     * @param hours количество часов для анализа
     * @return анализ паттернов дедлоков с рекомендациями
     * @throws BusinessException при ошибках анализа
     */
    DeadlockAnalysisResult analyzeDeadlockPatterns(Integer hours) throws BusinessException;

    /**
     * Принудительно завершает заблокированные транзакции.
     * ВНИМАНИЕ: Использовать только в критических ситуациях!
     *
     * @param maxBlockedTimeMinutes максимальное время блокировки в минутах
     * @param excludePids список PID для исключения из завершения
     * @return результат принудительного завершения
     * @throws BusinessException при ошибках завершения
     */
    ForceTerminationResult forceTerminateBlockedTransactions(
            Integer maxBlockedTimeMinutes, List<Integer> excludePids) throws BusinessException;

    /**
     * Выполняет стресс-тест для провокации дедлоков.
     * Используется для тестирования устойчивости системы.
     *
     * @param concurrentTransactions количество одновременных транзакций
     * @param testDurationSeconds длительность теста в секундах
     * @return результаты стресс-теста с метриками
     * @throws BusinessException при ошибках тестирования
     */
    DeadlockStressTestResult performDeadlockStressTest(
            Integer concurrentTransactions, Integer testDurationSeconds) throws BusinessException;

    /**
     * Предоставляет рекомендации по предотвращению дедлоков.
     * Анализирует текущие паттерны и предлагает улучшения.
     *
     * @param analysisData данные для анализа (SQL запросы, частота операций)
     * @return рекомендации по предотвращению дедлоков
     * @throws BusinessException при ошибках анализа
     */
    DeadlockPreventionRecommendations getDeadlockPreventionRecommendations(
            LockAnalysisData analysisData) throws BusinessException;
}
