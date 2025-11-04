package ru.mentee.power.service;

import ru.mentee.power.model.mp166.*;

/**
 * Service для демонстрации уровней изоляции транзакций.
 * Показывает разные аномалии чтения и их предотвращение.
 */
public interface IsolationLevelService {

  /**
   * Демонстрирует dirty reads с уровнем READ UNCOMMITTED.
   */
  DirtyReadResult demonstrateDirtyReads(Long accountId);

  /**
   * Демонстрирует non-repeatable reads с уровнем READ COMMITTED.
   */
  NonRepeatableReadResult demonstrateNonRepeatableReads(Long accountId);

  /**
   * Демонстрирует phantom reads с уровнем REPEATABLE READ.
   */
  PhantomReadResult demonstratePhantomReads(String status);

  /**
   * Моделирует конкурентное бронирование ограниченного товара.
   */
  ConcurrentBookingResult performConcurrentBooking(Long productId, Long userId, Integer quantity, String isolationLevel);

  /**
   * Запускает симуляцию высокой конкурентности для анализа производительности.
   */
  ConcurrencySimulationResult simulateHighConcurrency(Integer users, Integer operations, String isolationLevel);
}