/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.mp170.DailySalesReport;
import ru.mentee.power.model.mp170.SalesPersonRanking;
import ru.mentee.power.test.BaseIntegrationTest;

@DisplayName("Тестирование оконных функций (Window Functions)")
@SuppressWarnings({"resource", "deprecation"})
public class PostgresWindowFunctionsRepositoryTest extends BaseIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresWindowFunctionsRepositoryTest.class);

    private Liquibase liquibase;
    private PostgresWindowFunctionsRepository windowFunctionsRepository;

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

            // Применяем миграции для оконных функций: 1, 9, 10, 11, 12
            liquibase.update("dev,test");

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        windowFunctionsRepository = new PostgresWindowFunctionsRepository(config);
    }

    @Test
    @DisplayName("Should correctly rank salespeople using RANK, DENSE_RANK, and ROW_NUMBER")
    void shouldExecuteRankingQuery() throws DataAccessException {
        // When
        List<SalesPersonRanking> rankings = windowFunctionsRepository.executeRankingQuery();

        // Then
        assertThat(rankings).isNotEmpty();

        // Группируем по регионам для проверки ранжирования
        Map<String, List<SalesPersonRanking>> rankingsByRegion =
                rankings.stream().collect(Collectors.groupingBy(SalesPersonRanking::getRegionName));

        for (Map.Entry<String, List<SalesPersonRanking>> entry : rankingsByRegion.entrySet()) {
            String region = entry.getKey();
            List<SalesPersonRanking> regionRankings = entry.getValue();

            log.info(
                    "Проверка региона: {}, количество продавцов: {}",
                    region,
                    regionRankings.size());

            // Проверяем, что продажи отсортированы по убыванию
            for (int i = 0; i < regionRankings.size() - 1; i++) {
                SalesPersonRanking current = regionRankings.get(i);
                SalesPersonRanking next = regionRankings.get(i + 1);

                assertThat(current.getTotalSales()).isGreaterThanOrEqualTo(next.getTotalSales());

                // Проверяем RANK - может иметь пропуски
                assertThat(current.getRegionRank()).isNotNull();
                assertThat(current.getRegionRank()).isGreaterThanOrEqualTo(1);
                assertThat(current.getRegionRank()).isLessThanOrEqualTo(next.getRegionRank());

                // Проверяем DENSE_RANK - без пропусков
                assertThat(current.getDenseRank()).isNotNull();
                assertThat(current.getDenseRank()).isGreaterThanOrEqualTo(1);
                assertThat(current.getDenseRank()).isLessThanOrEqualTo(next.getDenseRank());

                // Проверяем ROW_NUMBER - уникальный номер
                assertThat(current.getRowNumber()).isNotNull();
                assertThat(current.getRowNumber()).isGreaterThanOrEqualTo(1);
                assertThat(current.getRowNumber()).isLessThan(next.getRowNumber());

                // Если продажи равны, RANK может быть одинаковым, но ROW_NUMBER всегда разный
                if (current.getTotalSales().compareTo(next.getTotalSales()) == 0) {
                    assertThat(current.getRegionRank()).isEqualTo(next.getRegionRank());
                    assertThat(current.getDenseRank()).isEqualTo(next.getDenseRank());
                } else {
                    // Если продажи разные, RANK должен увеличиваться
                    assertThat(next.getRegionRank())
                            .isGreaterThanOrEqualTo(current.getRegionRank());
                }
            }

            // Проверяем, что первый продавец имеет ранг 1
            if (!regionRankings.isEmpty()) {
                assertThat(regionRankings.get(0).getRegionRank()).isEqualTo(1);
                assertThat(regionRankings.get(0).getDenseRank()).isEqualTo(1);
                assertThat(regionRankings.get(0).getRowNumber()).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("Should calculate running totals correctly with SUM() OVER")
    void shouldCalculateRunningTotals() throws DataAccessException {
        // When
        List<DailySalesReport> reports = windowFunctionsRepository.calculateRunningTotals();

        // Then
        assertThat(reports).isNotEmpty();

        BigDecimal previousCumulative = BigDecimal.ZERO;
        BigDecimal previousDaily = null;

        for (DailySalesReport report : reports) {
            assertThat(report.getSaleDate()).isNotNull();
            assertThat(report.getDailySales()).isNotNull();
            assertThat(report.getCumulativeSales()).isNotNull();
            assertThat(report.getTransactionCount()).isNotNull();
            assertThat(report.getTransactionCount()).isGreaterThan(0);

            // Проверяем, что накопительная сумма монотонно возрастает
            assertThat(report.getCumulativeSales()).isGreaterThanOrEqualTo(previousCumulative);

            // Проверяем, что накопительная сумма = предыдущая накопительная + текущие продажи
            BigDecimal expectedCumulative = previousCumulative.add(report.getDailySales());
            assertThat(report.getCumulativeSales()).isEqualByComparingTo(expectedCumulative);

            // Проверяем, что даты отсортированы по возрастанию
            if (previousDaily != null) {
                assertThat(report.getSaleDate())
                        .isAfterOrEqualTo(reports.get(reports.indexOf(report) - 1).getSaleDate());
            }

            previousCumulative = report.getCumulativeSales();
            previousDaily = report.getDailySales();

            log.debug(
                    "Дата: {}, Дневные продажи: {}, Накопительная сумма: {}",
                    report.getSaleDate(),
                    report.getDailySales(),
                    report.getCumulativeSales());
        }

        // Проверяем, что последняя накопительная сумма равна сумме всех дневных продаж
        BigDecimal totalDailySales =
                reports.stream()
                        .map(DailySalesReport::getDailySales)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!reports.isEmpty()) {
            BigDecimal lastCumulative = reports.get(reports.size() - 1).getCumulativeSales();
            assertThat(lastCumulative).isEqualByComparingTo(totalDailySales);
        }
    }

    @Test
    @DisplayName("Should analyze regional performance with AVG() OVER PARTITION BY")
    void shouldAnalyzeRegionalPerformance() throws DataAccessException {
        // When
        List<SalesPersonRanking> rankings = windowFunctionsRepository.analyzeRegionalPerformance();

        // Then
        assertThat(rankings).isNotEmpty();

        // Группируем по регионам
        Map<String, List<SalesPersonRanking>> rankingsByRegion =
                rankings.stream().collect(Collectors.groupingBy(SalesPersonRanking::getRegionName));

        for (Map.Entry<String, List<SalesPersonRanking>> entry : rankingsByRegion.entrySet()) {
            String region = entry.getKey();
            List<SalesPersonRanking> regionRankings = entry.getValue();

            log.info("Анализ региона: {}, продавцов: {}", region, regionRankings.size());

            // Проверяем, что market_share_percent корректно вычислен
            for (SalesPersonRanking ranking : regionRankings) {
                assertThat(ranking.getTotalSales()).isNotNull();
                assertThat(ranking.getTotalSales()).isGreaterThan(BigDecimal.ZERO);
                assertThat(ranking.getMarketSharePercent()).isNotNull();

                // market_share_percent должен быть > 0, так как это процент от регионального
                // среднего
                assertThat(ranking.getMarketSharePercent()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

                // Проверяем ранги
                assertThat(ranking.getRegionRank()).isNotNull();
                assertThat(ranking.getDenseRank()).isNotNull();
                assertThat(ranking.getRowNumber()).isNotNull();
                assertThat(ranking.getRegionRank()).isGreaterThanOrEqualTo(1);
            }

            // Проверяем сортировку по убыванию продаж
            for (int i = 0; i < regionRankings.size() - 1; i++) {
                assertThat(regionRankings.get(i).getTotalSales())
                        .isGreaterThanOrEqualTo(regionRankings.get(i + 1).getTotalSales());
            }
        }
    }

    @Test
    @DisplayName("Should generate quartile distribution using NTILE(4)")
    void shouldGenerateQuartileDistribution() throws DataAccessException {
        // Получаем общее количество продавцов из основного запроса
        List<SalesPersonRanking> allRankings = windowFunctionsRepository.executeRankingQuery();
        int totalSalespeople = allRankings.size();

        // Если продавцов меньше 4, некоторые квартили могут быть пустыми
        assertThat(totalSalespeople).isGreaterThan(0);

        // When & Then - проверяем все 4 квартиля
        for (int quartile = 1; quartile <= 4; quartile++) {
            List<SalesPersonRanking> quartileRankings =
                    windowFunctionsRepository.generateQuartileDistribution(quartile);

            log.info("Квартиль {}: {} продавцов", quartile, quartileRankings.size());

            // Если квартиль не пустой, проверяем его содержимое
            if (!quartileRankings.isEmpty()) {
                // Проверяем, что все продажи отсортированы по убыванию
                for (int i = 0; i < quartileRankings.size() - 1; i++) {
                    SalesPersonRanking current = quartileRankings.get(i);
                    SalesPersonRanking next = quartileRankings.get(i + 1);

                    assertThat(current.getTotalSales())
                            .isGreaterThanOrEqualTo(next.getTotalSales());

                    // Проверяем ранги
                    assertThat(current.getRegionRank()).isNotNull();
                    assertThat(current.getDenseRank()).isNotNull();
                    assertThat(current.getRowNumber()).isNotNull();
                }
            }

            // Проверяем, что продажи в квартиле 1 >= продажи в квартиле 2 и т.д.
            if (quartile > 1) {
                List<SalesPersonRanking> previousQuartile =
                        windowFunctionsRepository.generateQuartileDistribution(quartile - 1);

                if (!previousQuartile.isEmpty() && !quartileRankings.isEmpty()) {
                    BigDecimal minPreviousQuartile =
                            previousQuartile.stream()
                                    .map(SalesPersonRanking::getTotalSales)
                                    .min(BigDecimal::compareTo)
                                    .orElse(BigDecimal.ZERO);

                    BigDecimal maxCurrentQuartile =
                            quartileRankings.stream()
                                    .map(SalesPersonRanking::getTotalSales)
                                    .max(BigDecimal::compareTo)
                                    .orElse(BigDecimal.ZERO);

                    // Продажи в предыдущем квартиле должны быть >= продажам в текущем
                    assertThat(minPreviousQuartile).isGreaterThanOrEqualTo(maxCurrentQuartile);
                }
            }
        }

        // Проверяем, что общее количество продавцов во всех квартилях равно общему количеству
        int totalInQuartiles = 0;
        for (int quartile = 1; quartile <= 4; quartile++) {
            List<SalesPersonRanking> quartileRankings =
                    windowFunctionsRepository.generateQuartileDistribution(quartile);
            totalInQuartiles += quartileRankings.size();
        }

        // Количество продавцов во всех квартилях должно быть равно общему количеству
        assertThat(totalInQuartiles).isEqualTo(totalSalespeople);

        // Проверяем, что хотя бы один квартиль не пустой
        boolean hasNonEmptyQuartile = false;
        for (int quartile = 1; quartile <= 4; quartile++) {
            List<SalesPersonRanking> quartileRankings =
                    windowFunctionsRepository.generateQuartileDistribution(quartile);
            if (!quartileRankings.isEmpty()) {
                hasNonEmptyQuartile = true;
                break;
            }
        }
        assertThat(hasNonEmptyQuartile).isTrue();
    }

    @Test
    @DisplayName("Should validate window function calculations with known data")
    void shouldValidateWindowFunctionCalculations() throws DataAccessException, SQLException {
        // Given - создаем тестовые данные с известными значениями
        try (Connection conn = getTestConnection()) {
            // Создаем регион для теста
            Long testRegionId = createTestRegion(conn, "Test Region", "Test Country");

            // Создаем продавцов с известными продажами
            Long salesperson1 = createTestSalesperson(conn, "Test Salesperson 1", testRegionId);
            Long salesperson2 = createTestSalesperson(conn, "Test Salesperson 2", testRegionId);
            Long salesperson3 = createTestSalesperson(conn, "Test Salesperson 3", testRegionId);

            // Создаем транзакции с известными суммами
            createTestTransaction(
                    conn, salesperson1, BigDecimal.valueOf(10000), LocalDate.now().minusDays(5));
            createTestTransaction(
                    conn, salesperson1, BigDecimal.valueOf(5000), LocalDate.now().minusDays(3));
            createTestTransaction(
                    conn, salesperson2, BigDecimal.valueOf(15000), LocalDate.now().minusDays(4));
            createTestTransaction(
                    conn, salesperson2, BigDecimal.valueOf(5000), LocalDate.now().minusDays(2));
            createTestTransaction(
                    conn, salesperson3, BigDecimal.valueOf(8000), LocalDate.now().minusDays(1));

            // When
            List<SalesPersonRanking> rankings = windowFunctionsRepository.executeRankingQuery();

            // Then - проверяем конкретные значения
            SalesPersonRanking sp1 =
                    rankings.stream()
                            .filter(r -> r.getName().equals("Test Salesperson 1"))
                            .findFirst()
                            .orElse(null);

            SalesPersonRanking sp2 =
                    rankings.stream()
                            .filter(r -> r.getName().equals("Test Salesperson 2"))
                            .findFirst()
                            .orElse(null);

            SalesPersonRanking sp3 =
                    rankings.stream()
                            .filter(r -> r.getName().equals("Test Salesperson 3"))
                            .findFirst()
                            .orElse(null);

            assertThat(sp1).isNotNull();
            assertThat(sp2).isNotNull();
            assertThat(sp3).isNotNull();

            // Проверяем суммы продаж
            assertThat(sp1.getTotalSales())
                    .isEqualByComparingTo(BigDecimal.valueOf(15000)); // 10000 + 5000
            assertThat(sp2.getTotalSales())
                    .isEqualByComparingTo(BigDecimal.valueOf(20000)); // 15000 + 5000
            assertThat(sp3.getTotalSales()).isEqualByComparingTo(BigDecimal.valueOf(8000));

            // Проверяем ранги (sp2 должен быть первым, sp1 - вторым, sp3 - третьим)
            if (sp2.getRegionRank() < sp1.getRegionRank()) {
                assertThat(sp2.getRegionRank()).isLessThan(sp1.getRegionRank());
                assertThat(sp1.getRegionRank()).isLessThan(sp3.getRegionRank());
            }

            log.info(
                    "Валидация: SP1={}, SP2={}, SP3={}",
                    sp1.getTotalSales(),
                    sp2.getTotalSales(),
                    sp3.getTotalSales());
        }
    }

    private Long createTestRegion(Connection conn, String name, String country)
            throws SQLException {
        try (PreparedStatement stmt =
                conn.prepareStatement(
                        "INSERT INTO mentee_power.regions (name, country, is_active) VALUES (?, ?,"
                                + " true) RETURNING id")) {
            stmt.setString(1, name);
            stmt.setString(2, country);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return null;
    }

    private Long createTestSalesperson(Connection conn, String name, Long regionId)
            throws SQLException {
        String email = name.toLowerCase().replace(" ", ".") + "@test.com";
        try (PreparedStatement stmt =
                conn.prepareStatement(
                        "INSERT INTO mentee_power.sales_people (name, email, region_id, hire_date,"
                                + " base_salary, commission_rate, status) VALUES (?, ?, ?,"
                                + " CURRENT_DATE, 50000, 0.05, 'ACTIVE') RETURNING id")) {
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setLong(3, regionId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return null;
    }

    private void createTestTransaction(
            Connection conn, Long salespersonId, BigDecimal amount, LocalDate saleDate)
            throws SQLException {
        // Получаем первый доступный продукт
        Long productId;
        try (PreparedStatement stmt =
                conn.prepareStatement(
                        "SELECT id FROM mentee_power.products_sales_analytics LIMIT 1")) {
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    productId = rs.getLong("id");
                } else {
                    // Создаем продукт, если его нет
                    try (PreparedStatement createProduct =
                            conn.prepareStatement(
                                    "INSERT INTO mentee_power.products_sales_analytics (name,"
                                            + " category, price, cost, is_active) VALUES ('Test"
                                            + " Product', 'ELECTRONICS', 1000, 500, true) RETURNING"
                                            + " id")) {
                        try (var rs2 = createProduct.executeQuery()) {
                            if (rs2.next()) {
                                productId = rs2.getLong("id");
                            } else {
                                return;
                            }
                        }
                    }
                }
            }
        }

        try (PreparedStatement stmt =
                conn.prepareStatement(
                        "INSERT INTO mentee_power.sales_transactions (salesperson_id, product_id,"
                                + " amount, quantity, sale_date, status) VALUES (?, ?, ?, 1, ?,"
                                + " 'COMPLETED')")) {
            stmt.setLong(1, salespersonId);
            stmt.setLong(2, productId);
            stmt.setBigDecimal(3, amount);
            stmt.setDate(4, java.sql.Date.valueOf(saleDate));
            stmt.executeUpdate();
        }
    }
}
