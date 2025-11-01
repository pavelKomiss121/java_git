/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.Order;
import ru.mentee.power.model.Product;
import ru.mentee.power.model.User;
import ru.mentee.power.model.mp162.IndexPerformanceTest;
import ru.mentee.power.model.mp162.IndexSizeInfo;
import ru.mentee.power.repository.interfaces.BTreeIndexRepository;

@Slf4j
public class PostgresBTreeIndexRepository implements BTreeIndexRepository {

    // Поиск пользователя по email
    private static final String FIND_USER_BY_EMAIL =
            """
    SELECT id, name, email, city, registration_date, is_active
    FROM users
    WHERE email = ? AND is_active = true
    """;

    // Загрузка заказов пользователя с сортировкой
    private static final String GET_USER_ORDERS =
            """
    SELECT id, user_id, total, status, created_at, region
    FROM orders
    WHERE user_id = ?
    ORDER BY created_at DESC
    LIMIT ? OFFSET ?
    """;

    // Поиск товаров по категории и цене
    private static final String FIND_PRODUCTS_BY_CATEGORY_AND_PRICE =
            """
    SELECT p.id, p.name, p.description, p.price, p.category_id, p.sku,
           p.created_at, c.name as category_name
    FROM products p
    JOIN categories c ON p.category_id = c.id
    WHERE p.category_id = ?
      AND p.price BETWEEN ? AND ?
    ORDER BY p.price ASC
    LIMIT ?
    """;

    // Создание B-Tree индексов (каждая команда отдельно, т.к. CONCURRENTLY нельзя в пайплайне)
    // Не указываем схему явно - используем search_path, установленный в getConnection()
    // Примечание: В PostgreSQL 9.5+ IF NOT EXISTS поддерживается с CONCURRENTLY
    private static final String[] CREATE_BTREE_INDEXES = {
        "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_active ON"
                + " users(email) WHERE is_active = true",
        "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_user_created_desc ON"
                + " orders(user_id, created_at DESC)",
        "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_category_price ON"
                + " products(category_id, price)"
    };

    // Информация о размерах индексов
    private static final String INDEX_SIZE_INFO =
            """
    SELECT
        pi.indexname,
        pi.tablename,
        CASE
            WHEN pi.indexdef LIKE '%USING btree%' THEN 'btree'
            WHEN pi.indexdef LIKE '%USING gin%' THEN 'gin'
            WHEN pi.indexdef LIKE '%USING gist%' THEN 'gist'
            ELSE 'btree'
        END as index_type,
        pg_size_pretty(pg_relation_size((pi.schemaname||'.'||pi.indexname)::regclass)) as size_human,
        pg_relation_size((pi.schemaname||'.'||pi.indexname)::regclass) as size_bytes,
        COALESCE(psui.idx_scan, 0) as tuples,
        pi.indexdef as definition
    FROM pg_indexes pi
    LEFT JOIN pg_stat_user_indexes psui
        ON pi.schemaname = psui.schemaname
        AND pi.indexname = psui.indexrelname::text
    WHERE (pi.schemaname = 'mentee_power' OR pi.schemaname = 'public' OR pi.schemaname = current_schema())
      AND pi.indexname LIKE 'idx_%'
      AND pi.indexname IN ('idx_users_email_active', 'idx_orders_user_created_desc', 'idx_products_category_price')
    ORDER BY pg_relation_size((pi.schemaname||'.'||pi.indexname)::regclass) DESC
    """;

    private static final String EXPLAIN_ANALYZE_WRAPPER =
            """
    EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) %s
    """;

    /**
     * Внутренний класс для хранения метрик плана выполнения
     */
    private static class ExecutionPlanMetrics {
        String queryPlan;
        String operationType;
        Long buffersHit;
        Long buffersRead;
        Long rowsScanned;
        Long rowsReturned;
        String indexUsed;
        BigDecimal costEstimate;

        ExecutionPlanMetrics(String queryPlan) {
            this.queryPlan = queryPlan;
        }
    }

    private ApplicationConfig config;

    public PostgresBTreeIndexRepository(ApplicationConfig config) {
        this.config = config;
    }

    protected Connection getConnection() throws SQLException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        // Устанавливаем search_path на нужную схему
        try (PreparedStatement stmt =
                conn.prepareStatement("SET search_path TO mentee_power, public")) {
            stmt.execute();
        }
        return conn;
    }

    private String determinePerformanceGrade(long executionTimeMs) {
        if (executionTimeMs < 100) return "EXCELLENT";
        if (executionTimeMs < 500) return "GOOD";
        if (executionTimeMs < 2000) return "POOR";
        return "CRITICAL";
    }

    private ExecutionPlanMetrics parseExecutionPlanMetrics(String queryPlan) {
        ExecutionPlanMetrics metrics = new ExecutionPlanMetrics(queryPlan);

        if (queryPlan == null || queryPlan.isEmpty()) {
            return metrics;
        }

        // Извлекаем индекс, если используется (проверяем в первую очередь)
        metrics.indexUsed = extractString(queryPlan, "\"Index Name\"\\s*:\\s*\"([^\"]+)\"");

        // Извлекаем тип операции
        // Сначала пытаемся найти индексные операции (Index Scan, Bitmap Index Scan, Bitmap Heap
        // Scan)
        // так как они могут быть в дочерних узлах, а не в корневом
        String indexScanType =
                extractString(
                        queryPlan,
                        "\"Node Type\"\\s*:\\s*\"(Index Scan|Bitmap Index Scan|Bitmap Heap"
                                + " Scan)\"");
        if (indexScanType != null) {
            metrics.operationType = indexScanType;
        } else {
            // Если индексных операций нет, берем корневой узел
            metrics.operationType = extractString(queryPlan, "\"Node Type\"\\s*:\\s*\"([^\"]+)\"");
        }

        // Извлекаем buffers (попадания в кэш и чтения с диска)
        metrics.buffersHit = extractLong(queryPlan, "\"shared hit blocks\"\\s*:\\s*(\\d+)");
        metrics.buffersRead = extractLong(queryPlan, "\"shared read blocks\"\\s*:\\s*(\\d+)");

        // Извлекаем количество строк
        metrics.rowsScanned = extractLong(queryPlan, "\"Actual Rows\"\\s*:\\s*(\\d+)");
        metrics.rowsReturned = metrics.rowsScanned; // Для простых запросов обычно одинаково

        // Извлекаем оценку стоимости запроса
        metrics.costEstimate =
                extractBigDecimal(queryPlan, "\"Total Cost\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");

        return metrics;
    }

    private <T> IndexPerformanceTest<T> buildIndexPerformanceTest(
            T data,
            long executionTimeNanos,
            long executionTimeMs,
            LocalDateTime executedAt,
            ExecutionPlanMetrics metrics) {

        return IndexPerformanceTest.<T>builder()
                .data(data)
                .executionTimeNanos(executionTimeNanos)
                .executionTimeMs(executionTimeMs)
                .queryPlan(metrics.queryPlan)
                .operationType(metrics.operationType)
                .buffersHit(metrics.buffersHit)
                .buffersRead(metrics.buffersRead)
                .rowsScanned(metrics.rowsScanned)
                .rowsReturned(metrics.rowsReturned)
                .performanceGrade(determinePerformanceGrade(executionTimeMs))
                .executedAt(executedAt)
                .indexUsed(metrics.indexUsed)
                .costEstimate(metrics.costEstimate)
                .build();
    }

    private String getExecutionPlanJson(Connection connection, String query) {
        try {
            String explainQuery = String.format(EXPLAIN_ANALYZE_WRAPPER, query);

            try (PreparedStatement explainStmt = connection.prepareStatement(explainQuery);
                    ResultSet explainRs = explainStmt.executeQuery()) {

                if (explainRs.next()) {
                    return explainRs.getString(1);
                }
            }
        } catch (SQLException e) {
            // Если не удалось получить план, возвращаем null
        }
        return null;
    }

    private String extractString(String json, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private Long extractLong(String json, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private BigDecimal extractBigDecimal(String json, String pattern) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(json);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private IndexPerformanceTest<Optional<User>> executeFindUserByEmail(String email)
            throws DataAccessException {
        long startTimeNanos = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();
        Optional<User> user = Optional.empty();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_USER_BY_EMAIL)) {

            statement.setString(1, email);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    user =
                            Optional.of(
                                    User.builder()
                                            .id(rs.getLong("id"))
                                            .name(rs.getString("name"))
                                            .email(rs.getString("email"))
                                            .city(rs.getString("city"))
                                            .registration_date(
                                                    rs.getTimestamp("registration_date")
                                                            .toLocalDateTime())
                                            .is_active(rs.getBoolean("is_active"))
                                            .build());
                }
            }

            // Получаем план выполнения для анализа
            String queryStr =
                    String.format(
                            "SELECT id, name, email, city, registration_date, is_active "
                                    + "FROM users WHERE email = '%s' AND is_active = true",
                            email.replace("'", "''"));

            String queryPlan = getExecutionPlanJson(connection, queryStr);
            ExecutionPlanMetrics metrics = parseExecutionPlanMetrics(queryPlan);

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            long executionTimeMs = System.currentTimeMillis() - startTimeMs;

            return buildIndexPerformanceTest(
                    user, executionTimeNanos, executionTimeMs, executedAt, metrics);

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка поиска пользователя по email", e);
        }
    }

    @Override
    public IndexPerformanceTest<Optional<User>> findUserByEmailWithoutIndex(String email)
            throws DataAccessException {
        dropBTreeIndexes();
        return executeFindUserByEmail(email);
    }

    @Override
    public IndexPerformanceTest<Optional<User>> findUserByEmailWithIndex(String email)
            throws DataAccessException {
        createBTreeIndexes();
        return executeFindUserByEmail(email);
    }

    private IndexPerformanceTest<List<Order>> executeGetUserOrders(
            Long userId, Integer limit, Integer offset) throws DataAccessException {
        long startTimeNanos = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();
        List<Order> orders = new java.util.ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(GET_USER_ORDERS)) {

            statement.setLong(1, userId);
            statement.setInt(2, limit);
            statement.setInt(3, offset);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(
                            Order.builder()
                                    .id(rs.getLong("id"))
                                    .userId(rs.getLong("user_id"))
                                    .total(rs.getBigDecimal("total"))
                                    .status(rs.getString("status"))
                                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                                    .region(rs.getString("region"))
                                    .build());
                }
            }

            String queryStr =
                    String.format(
                            "SELECT id, user_id, total, status, created_at, region FROM orders"
                                + " WHERE user_id = %d ORDER BY created_at DESC LIMIT %d OFFSET %d",
                            userId, limit, offset);

            String queryPlan = getExecutionPlanJson(connection, queryStr);
            ExecutionPlanMetrics metrics = parseExecutionPlanMetrics(queryPlan);

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            long executionTimeMs = System.currentTimeMillis() - startTimeMs;

            return buildIndexPerformanceTest(
                    orders, executionTimeNanos, executionTimeMs, executedAt, metrics);

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка запроса получения заказов", e);
        }
    }

    @Override
    public IndexPerformanceTest<List<Order>> getUserOrdersWithoutIndex(
            Long userId, Integer limit, Integer offset) throws DataAccessException {
        dropBTreeIndexes();
        return executeGetUserOrders(userId, limit, offset);
    }

    @Override
    public IndexPerformanceTest<List<Order>> getUserOrdersWithIndex(
            Long userId, Integer limit, Integer offset) throws DataAccessException {
        createBTreeIndexes();
        return executeGetUserOrders(userId, limit, offset);
    }

    private IndexPerformanceTest<List<Product>> executeFindProductsByCategoryAndPrice(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer limit)
            throws DataAccessException {
        long startTimeNanos = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();
        List<Product> products = new java.util.ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(FIND_PRODUCTS_BY_CATEGORY_AND_PRICE)) {

            statement.setLong(1, categoryId);
            statement.setBigDecimal(2, minPrice);
            statement.setBigDecimal(3, maxPrice);
            statement.setInt(4, limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    products.add(
                            Product.builder()
                                    .id(rs.getLong("id"))
                                    .name(rs.getString("name"))
                                    .description(rs.getString("description"))
                                    .price(rs.getBigDecimal("price"))
                                    .categoryId(rs.getLong("category_id"))
                                    .sku(rs.getString("sku"))
                                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                                    .categoryName(rs.getString("category_name"))
                                    .build());
                }
            }

            // Получаем план выполнения для анализа
            String queryStr =
                    String.format(
                            "SELECT p.id, p.name, p.description, p.price, p.category_id, p.sku,"
                                + " p.created_at, c.name as category_name FROM products p JOIN"
                                + " categories c ON p.category_id = c.id WHERE p.category_id = %d"
                                + " AND p.price BETWEEN %s AND %s ORDER BY p.price ASC LIMIT %d",
                            categoryId, minPrice, maxPrice, limit);

            String queryPlan = getExecutionPlanJson(connection, queryStr);
            ExecutionPlanMetrics metrics = parseExecutionPlanMetrics(queryPlan);

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            long executionTimeMs = System.currentTimeMillis() - startTimeMs;

            return buildIndexPerformanceTest(
                    products, executionTimeNanos, executionTimeMs, executedAt, metrics);

        } catch (SQLException e) {
            throw new DataAccessException("Ошибка поиска товаров по категории и цене", e);
        }
    }

    @Override
    public IndexPerformanceTest<List<Product>> findProductsByCategoryAndPriceWithoutIndex(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer limit)
            throws DataAccessException {
        dropBTreeIndexes();
        return executeFindProductsByCategoryAndPrice(categoryId, minPrice, maxPrice, limit);
    }

    @Override
    public IndexPerformanceTest<List<Product>> findProductsByCategoryAndPriceWithIndex(
            Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer limit)
            throws DataAccessException {
        createBTreeIndexes();
        return executeFindProductsByCategoryAndPrice(categoryId, minPrice, maxPrice, limit);
    }

    @Override
    public IndexPerformanceTest<String> createBTreeIndexes() throws DataAccessException {
        long startTimeNanos = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();

        log.info("Начинаем создание B-Tree индексов...");

        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {

            // Выполняем каждую команду отдельно (CREATE INDEX CONCURRENTLY нельзя в пайплайне)
            for (String createIndex : CREATE_BTREE_INDEXES) {
                log.info("Выполняем: {}", createIndex);
                try {
                    statement.execute(createIndex);
                    log.info("Индекс создан успешно");
                } catch (SQLException e) {
                    // IF NOT EXISTS обрабатывает случай, когда индекс уже существует,
                    // но может быть ошибка, если таблица не существует
                    String errorMessage = e.getMessage();
                    if (errorMessage != null && errorMessage.contains("already exists")) {
                        log.info("Индекс уже существует, пропускаем создание");
                    } else if (errorMessage != null && errorMessage.contains("does not exist")) {
                        log.error(
                                "Таблица не найдена. Проверьте, что миграции применены и"
                                        + " search_path настроен правильно. Ошибка: {}",
                                errorMessage);
                        throw e;
                    } else {
                        log.error("Ошибка при создании индекса: {}", createIndex, e);
                        throw e;
                    }
                }
            }

            // Ждем завершения CONCURRENTLY индексов
            log.info("Ожидаем завершения создания индексов...");
            try (PreparedStatement waitStmt =
                    connection.prepareStatement(
                            "SELECT COUNT(*) FROM pg_stat_progress_create_index WHERE datname ="
                                    + " current_database()")) {
                int waitingCount = 1;
                int attempts = 0;
                while (waitingCount > 0 && attempts < 30) {
                    try (ResultSet rs = waitStmt.executeQuery()) {
                        if (rs.next()) {
                            waitingCount = rs.getInt(1);
                        }
                    }
                    if (waitingCount > 0) {
                        Thread.sleep(100);
                        attempts++;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Проверяем индексы напрямую через pg_indexes
            log.info("Проверяем созданные индексы через прямой SQL запрос...");
            try (PreparedStatement checkStmt =
                    connection.prepareStatement(
                            "SELECT schemaname, indexname, tablename FROM pg_indexes WHERE"
                                    + " indexname LIKE 'idx_%'")) {
                try (ResultSet rs = checkStmt.executeQuery()) {
                    log.info("Найденные индексы в pg_indexes:");
                    while (rs.next()) {
                        log.info(
                                "  - Схема: {}, Индекс: {}, Таблица: {}",
                                rs.getString("schemaname"),
                                rs.getString("indexname"),
                                rs.getString("tablename"));
                    }
                }
            }

            // Проверяем, что индексы действительно созданы
            List<IndexSizeInfo> indexesAfterCreate = getIndexSizeInformation();
            log.info(
                    "После создания найдено индексов через getIndexSizeInformation(): {}",
                    indexesAfterCreate.size());
            for (IndexSizeInfo idx : indexesAfterCreate) {
                log.info(
                        "  - Индекс: {} на таблице: {}, тип: {}, размер: {}",
                        idx.getIndexName(),
                        idx.getTableName(),
                        idx.getIndexType(),
                        idx.getSizeHuman());
            }

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            long executionTimeMs = System.currentTimeMillis() - startTimeMs;

            log.info("Создание индексов завершено за {} мс", executionTimeMs);

            return buildIndexPerformanceTest(
                    "Индексы успешно созданы",
                    executionTimeNanos,
                    executionTimeMs,
                    executedAt,
                    new ExecutionPlanMetrics(null));

        } catch (SQLException e) {
            log.error("Ошибка создания индексов", e);
            throw new DataAccessException("Ошибка создания индексов", e);
        }
    }

    @Override
    public IndexPerformanceTest<String> dropBTreeIndexes() throws DataAccessException {
        long startTimeNanos = System.nanoTime();
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime executedAt = LocalDateTime.now();

        log.info("Начинаем удаление B-Tree индексов...");

        // Проверяем индексы ДО удаления
        List<IndexSizeInfo> indexesBeforeDrop = getIndexSizeInformation();
        log.info("Перед удалением найдено индексов: {}", indexesBeforeDrop.size());
        for (IndexSizeInfo idx : indexesBeforeDrop) {
            log.info("  - Индекс: {} на таблице: {}", idx.getIndexName(), idx.getTableName());
        }

        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {

            // Выполняем каждую команду отдельно
            // Удаляем индексы по именам из всех возможных схем
            String[] indexNames = {
                "idx_users_email_active",
                "idx_orders_user_created_desc",
                "idx_products_category_price"
            };
            for (String indexName : indexNames) {
                // Пробуем удалить из всех возможных схем
                for (String schema : new String[] {"mentee_power", "public", ""}) {
                    String dropCmd =
                            schema.isEmpty()
                                    ? "DROP INDEX IF EXISTS " + indexName
                                    : "DROP INDEX IF EXISTS " + schema + "." + indexName;
                    try {
                        log.debug("Пытаемся удалить: {}", dropCmd);
                        statement.execute(dropCmd);
                        log.debug("Команда выполнена: {}", dropCmd);
                    } catch (SQLException e) {
                        // Игнорируем ошибки - индекс может уже не существовать или быть в другой
                        // схеме
                        log.trace(
                                "Индекс {} не найден в схеме {} (это нормально)",
                                indexName,
                                schema.isEmpty() ? "текущей" : schema);
                    }
                }
            }

            // Проверяем, что индексы действительно удалены
            List<IndexSizeInfo> indexesAfterDrop = getIndexSizeInformation();
            log.info("После удаления найдено индексов: {}", indexesAfterDrop.size());
            if (!indexesAfterDrop.isEmpty()) {
                log.warn("ВНИМАНИЕ: После удаления остались индексы:");
                for (IndexSizeInfo idx : indexesAfterDrop) {
                    log.warn(
                            "  - Индекс: {} на таблице: {}",
                            idx.getIndexName(),
                            idx.getTableName());
                }
            } else {
                log.info("Все индексы успешно удалены");
            }

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            long executionTimeMs = System.currentTimeMillis() - startTimeMs;

            log.info("Удаление индексов завершено за {} мс", executionTimeMs);

            return buildIndexPerformanceTest(
                    "Индексы успешно удалены",
                    executionTimeNanos,
                    executionTimeMs,
                    executedAt,
                    new ExecutionPlanMetrics(null));

        } catch (SQLException e) {
            log.error("Ошибка удаления индексов", e);
            throw new DataAccessException("Ошибка удаления индексов", e);
        }
    }

    @Override
    public List<IndexSizeInfo> getIndexSizeInformation() throws DataAccessException {
        List<IndexSizeInfo> indexInfoList = new java.util.ArrayList<>();

        log.debug("Выполняем запрос для получения информации об индексах");
        log.trace("SQL запрос: {}", INDEX_SIZE_INFO);

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(INDEX_SIZE_INFO);
                ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                indexInfoList.add(
                        IndexSizeInfo.builder()
                                .indexName(rs.getString("indexname"))
                                .tableName(rs.getString("tablename"))
                                .indexType(rs.getString("index_type"))
                                .sizeBytes(rs.getLong("size_bytes"))
                                .sizeHuman(rs.getString("size_human"))
                                .tuples(rs.getLong("tuples"))
                                .definition(rs.getString("definition"))
                                .build());
            }

            log.debug("Найдено индексов: {}", indexInfoList.size());

        } catch (SQLException e) {
            log.error("Ошибка получения информации о размерах индексов", e);
            throw new DataAccessException("Ошибка получения информации о размерах индексов", e);
        }

        return indexInfoList;
    }
}
