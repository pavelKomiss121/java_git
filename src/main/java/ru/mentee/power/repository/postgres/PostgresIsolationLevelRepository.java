package ru.mentee.power.repository.postgres;

import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp166.*;
import ru.mentee.power.repository.interfaces.IsolationLevelRepository;
import ru.mentee.power.service.IsolationLevelService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PostgresIsolationLevelRepository implements IsolationLevelRepository, IsolationLevelService {


  @Override
  public <T> T executeWithIsolationLevel(String isolationLevel, TransactionOperation<T> operation) throws DataAccessException {
    return null;
  }

  @Override
  public TransactionContext startTransactionWithLevel(String isolationLevel) {
    return null;
  }

  @Override
  public OperationResult performConcurrentOperation(TransactionContext context, String operation, Map<String, Object> params) {
    return null;
  }

  @Override
  public DirtyReadResult demonstrateDirtyReads(Long accountId) {
    return null;
  }

  @Override
  public NonRepeatableReadResult demonstrateNonRepeatableReads(Long accountId) {
    return null;
  }

  @Override
  public PhantomReadResult demonstratePhantomReads(String status) {
    return null;
  }

  @Override
  public ConcurrentBookingResult performConcurrentBooking(Long productId, Long userId, Integer quantity, String isolationLevel) {
    return null;
  }

  @Override
  public ConcurrencySimulationResult simulateHighConcurrency(Integer users, Integer operations, String isolationLevel) {
    return null;
  }
}
