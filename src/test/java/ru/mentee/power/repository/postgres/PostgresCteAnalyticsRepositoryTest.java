/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.exception.SASTException;
import ru.mentee.power.model.mp171.AbcAnalysisReport;
import ru.mentee.power.model.mp171.CategoryHierarchyReport;
import ru.mentee.power.model.mp171.CohortAnalysisReport;
import ru.mentee.power.model.mp171.CustomerSegmentReport;
import ru.mentee.power.model.mp171.ProductTrendReport;
import ru.mentee.power.test.BaseIntegrationTest;

@DisplayName("Тестирование CTE (Common Table Expression) аналитики")
@SuppressWarnings({"resource", "deprecation"})
@Disabled("Урок пройден")
public class PostgresCteAnalyticsRepositoryTest extends BaseIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresCteAnalyticsRepositoryTest.class);

    private Liquibase liquibase;
    private PostgresCteAnalyticsRepository cteRepository;

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

            // Применяем все миграции включая CTE аналитику (013)
            liquibase.update("dev,test");

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Liquibase", e);
        }

        cteRepository = new PostgresCteAnalyticsRepository(config);
        // Тестовые данные создаются в миграции 014-create-cte-analytics-test-data.sql
    }

    @Test
    @DisplayName("Should correctly segment customers using multiple CTE")
    void shouldExecuteMultipleCte() throws DataAccessException {
        // When
        List<CustomerSegmentReport> reports = cteRepository.executeMultipleCte();

        // Then
        assertThat(reports).isNotEmpty();

        // Группируем по сегментам
        Map<String, List<CustomerSegmentReport>> bySegment =
                reports.stream().collect(Collectors.groupingBy(CustomerSegmentReport::getSegment));

        log.info("Найдено сегментов: {}", bySegment.size());

        // Проверяем наличие основных сегментов
        assertThat(bySegment).containsKeys("VIP", "PREMIUM", "REGULAR", "NEW", "INACTIVE");

        // Проверяем VIP сегмент
        List<CustomerSegmentReport> vipReports = bySegment.get("VIP");
        if (vipReports != null && !vipReports.isEmpty()) {
            CustomerSegmentReport vip = vipReports.get(0);
            assertThat(vip.getCustomersCount()).isGreaterThan(0);
            assertThat(vip.getSegmentRevenue()).isGreaterThan(new BigDecimal("50000"));
            assertThat(vip.getRevenueSharePercent()).isNotNull();
        }

        // Проверяем, что revenue_share_percent в сумме ≈ 100%
        BigDecimal totalShare =
                reports.stream()
                        .map(CustomerSegmentReport::getRevenueSharePercent)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Общая доля выручки: {}%", totalShare);
        assertThat(totalShare).isBetween(new BigDecimal("99.0"), new BigDecimal("101.0"));

        // Проверяем, что все отчеты имеют необходимые поля
        for (CustomerSegmentReport report : reports) {
            assertThat(report.getSegment()).isNotNull();
            assertThat(report.getActivityStatus()).isNotNull();
            assertThat(report.getCustomersCount()).isGreaterThanOrEqualTo(0);
            assertThat(report.getSegmentRevenue()).isNotNull();
        }
    }

    @Test
    @DisplayName("Should correctly build category hierarchy using recursive CTE")
    void shouldExecuteRecursiveCte() throws DataAccessException {
        // When
        List<CategoryHierarchyReport> reports = cteRepository.executeRecursiveCte();

        // Then
        assertThat(reports).isNotEmpty();

        // Проверяем наличие корневых категорий (level = 0)
        List<CategoryHierarchyReport> rootCategories =
                reports.stream().filter(r -> r.getLevel() == 0).collect(Collectors.toList());

        assertThat(rootCategories).isNotEmpty();
        log.info("Найдено корневых категорий: {}", rootCategories.size());

        // Проверяем иерархию
        for (CategoryHierarchyReport report : reports) {
            assertThat(report.getLevel()).isGreaterThanOrEqualTo(0);
            assertThat(report.getLevel()).isLessThan(10); // Ограничение глубины
            assertThat(report.getFullPath()).isNotNull();
            assertThat(report.getIndentedName()).isNotNull();
            assertThat(report.getProductsCount()).isGreaterThanOrEqualTo(0);
            assertThat(report.getTotalRevenue()).isNotNull();

            // Проверяем, что full_path содержит имя категории
            assertThat(report.getFullPath()).isNotEmpty();

            // Проверяем, что indented_name имеет правильные отступы
            int expectedIndent = report.getLevel() * 2;
            String indent =
                    report.getIndentedName()
                            .substring(
                                    0, Math.min(expectedIndent, report.getIndentedName().length()));
            if (report.getLevel() > 0) {
                assertThat(indent.length()).isLessThanOrEqualTo(expectedIndent);
            }
        }

        // Проверяем, что дочерние категории имеют level > 0
        List<CategoryHierarchyReport> childCategories =
                reports.stream().filter(r -> r.getLevel() > 0).collect(Collectors.toList());

        if (!childCategories.isEmpty()) {
            log.info("Найдено дочерних категорий: {}", childCategories.size());
            for (CategoryHierarchyReport child : childCategories) {
                assertThat(child.getFullPath()).contains(" > ");
            }
        }
    }

    @Test
    @DisplayName("Should correctly perform cohort analysis using CTE")
    void shouldPerformCohortAnalysis() throws DataAccessException {
        // When
        List<CohortAnalysisReport> reports = cteRepository.performCohortAnalysis();

        // Then
        assertThat(reports).isNotEmpty();

        // Группируем по когортам
        Map<LocalDate, List<CohortAnalysisReport>> byCohort =
                reports.stream()
                        .filter(r -> r.getCohortMonth() != null)
                        .collect(Collectors.groupingBy(CohortAnalysisReport::getCohortMonth));

        log.info("Найдено когорт: {}", byCohort.size());

        for (Map.Entry<LocalDate, List<CohortAnalysisReport>> entry : byCohort.entrySet()) {
            List<CohortAnalysisReport> cohortReports = entry.getValue();

            // Проверяем, что есть запись для месяца 0 (месяц регистрации)
            CohortAnalysisReport month0 =
                    cohortReports.stream()
                            .filter(r -> r.getMonthNumber() == 0)
                            .findFirst()
                            .orElse(null);

            if (month0 != null) {
                assertThat(month0.getRetentionRate()).isEqualByComparingTo(new BigDecimal("100.0"));
                assertThat(month0.getCohortSize()).isGreaterThan(0);
                assertThat(month0.getActiveCustomers()).isEqualTo(month0.getCohortSize());
            }

            // Проверяем логику retention rate
            for (CohortAnalysisReport report : cohortReports) {
                assertThat(report.getCohortMonth()).isNotNull();
                assertThat(report.getCohortSize()).isGreaterThan(0);
                assertThat(report.getMonthNumber()).isGreaterThanOrEqualTo(0);
                assertThat(report.getActiveCustomers()).isLessThanOrEqualTo(report.getCohortSize());
                assertThat(report.getRetentionRate())
                        .isBetween(BigDecimal.ZERO, new BigDecimal("100.0"));

                // Retention rate должен быть <= 100%
                if (report.getMonthNumber() > 0) {
                    BigDecimal expectedRetention =
                            new BigDecimal(report.getActiveCustomers())
                                    .divide(
                                            new BigDecimal(report.getCohortSize()),
                                            2,
                                            RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"));
                    assertThat(report.getRetentionRate())
                            .isCloseTo(expectedRetention, within(new BigDecimal("0.1")));
                }
            }
        }
    }

    @Test
    @DisplayName("Should correctly perform ABC analysis using CTE with window functions")
    void shouldPerformAbcAnalysis() throws DataAccessException {
        // When
        List<AbcAnalysisReport> reports = cteRepository.performAbcAnalysis();

        // Then
        assertThat(reports).isNotEmpty();

        // Проверяем, что продукты отсортированы по рангу
        for (int i = 0; i < reports.size() - 1; i++) {
            AbcAnalysisReport current = reports.get(i);
            AbcAnalysisReport next = reports.get(i + 1);

            // RANK() может давать одинаковые ранги для продуктов с одинаковым доходом
            if (current.getRevenueRank().equals(next.getRevenueRank())) {
                // Если ранги одинаковые, то доход должен быть одинаковый
                assertThat(current.getTotalRevenue()).isEqualByComparingTo(next.getTotalRevenue());
            } else {
                // Если ранги разные, то текущий ранг должен быть меньше следующего
                assertThat(current.getRevenueRank()).isLessThan(next.getRevenueRank());
                assertThat(current.getTotalRevenue())
                        .isGreaterThanOrEqualTo(next.getTotalRevenue());
            }
        }

        // Проверяем ABC категории
        Map<String, List<AbcAnalysisReport>> byCategory =
                reports.stream().collect(Collectors.groupingBy(AbcAnalysisReport::getAbcCategory));

        assertThat(byCategory).containsKeys("A", "B", "C");

        // Проверяем логику ABC категорий
        BigDecimal totalRevenue =
                reports.stream()
                        .map(AbcAnalysisReport::getTotalRevenue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cumulativeRevenue = BigDecimal.ZERO;
        for (AbcAnalysisReport report : reports) {
            cumulativeRevenue = cumulativeRevenue.add(report.getTotalRevenue());

            BigDecimal cumulativePercent =
                    cumulativeRevenue
                            .divide(totalRevenue, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));

            // Проверяем соответствие категории накопительному проценту
            if (cumulativePercent.compareTo(new BigDecimal("80")) <= 0) {
                assertThat(report.getAbcCategory()).isEqualTo("A");
            } else if (cumulativePercent.compareTo(new BigDecimal("95")) <= 0) {
                assertThat(report.getAbcCategory()).isIn("A", "B");
            } else {
                assertThat(report.getAbcCategory()).isIn("B", "C");
            }

            // Проверяем накопительные значения
            assertThat(report.getCumulativeRevenue()).isEqualByComparingTo(cumulativeRevenue);
            assertThat(report.getCumulativePercent())
                    .isCloseTo(cumulativePercent, within(new BigDecimal("0.1")));
        }

        // Проверяем, что все поля заполнены
        for (AbcAnalysisReport report : reports) {
            assertThat(report.getProductId()).isNotNull();
            assertThat(report.getProductName()).isNotNull();
            assertThat(report.getTotalRevenue()).isNotNull();
            assertThat(report.getRevenueRank()).isGreaterThan(0);
            assertThat(report.getAbcCategory()).isIn("A", "B", "C");
        }
    }

    @Test
    @DisplayName("Should correctly analyze product trends using CTE with LAG()")
    void shouldPerformProductTrendsAnalysis() throws DataAccessException {
        // When
        List<ProductTrendReport> reports = cteRepository.performProductTrendsAnalysis();

        // Then
        assertThat(reports).isNotEmpty();

        // Группируем по продуктам
        Map<String, List<ProductTrendReport>> byProduct =
                reports.stream().collect(Collectors.groupingBy(ProductTrendReport::getProductName));

        log.info("Найдено продуктов с трендами: {}", byProduct.size());

        for (Map.Entry<String, List<ProductTrendReport>> entry : byProduct.entrySet()) {
            List<ProductTrendReport> productReports = entry.getValue();

            // Сортируем по месяцам
            productReports.sort(
                    (a, b) -> {
                        if (a.getSalesMonth() == null || b.getSalesMonth() == null) {
                            return 0;
                        }
                        return a.getSalesMonth().compareTo(b.getSalesMonth());
                    });

            // Проверяем логику роста
            for (int i = 1; i < productReports.size(); i++) {
                ProductTrendReport current = productReports.get(i);
                ProductTrendReport previous = productReports.get(i - 1);

                // Если есть предыдущий месяц, должен быть рассчитан рост
                if (current.getRevenueGrowthPercent() != null) {
                    BigDecimal expectedGrowth =
                            current.getRevenue()
                                    .subtract(previous.getRevenue())
                                    .divide(previous.getRevenue(), 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"));

                    assertThat(current.getRevenueGrowthPercent())
                            .isCloseTo(expectedGrowth, within(new BigDecimal("0.1")));
                }

                // Проверяем категорию тренда
                if (current.getRevenueGrowthPercent() != null) {
                    BigDecimal growth = current.getRevenueGrowthPercent();
                    if (growth.compareTo(new BigDecimal("20")) > 0) {
                        assertThat(current.getTrendCategory()).isEqualTo("GROWING_FAST");
                    } else if (growth.compareTo(new BigDecimal("5")) > 0) {
                        assertThat(current.getTrendCategory()).isEqualTo("GROWING");
                    } else if (growth.compareTo(new BigDecimal("-5")) > 0) {
                        assertThat(current.getTrendCategory()).isEqualTo("STABLE");
                    } else {
                        assertThat(current.getTrendCategory()).isEqualTo("DECLINING");
                    }
                } else {
                    // Первый месяц - новый продукт
                    assertThat(current.getTrendCategory()).isEqualTo("NEW");
                }
            }

            // Проверяем, что все поля заполнены
            for (ProductTrendReport report : productReports) {
                assertThat(report.getProductName()).isNotNull();
                assertThat(report.getSalesMonth()).isNotNull();
                assertThat(report.getRevenue()).isNotNull();
                assertThat(report.getUnitsSold()).isGreaterThanOrEqualTo(0);
                assertThat(report.getTrendCategory()).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("Should process hierarchical data correctly")
    void shouldProcessHierarchicalData() throws DataAccessException {
        // When
        List<CategoryHierarchyReport> reports = cteRepository.processHierarchicalData();

        // Then
        assertThat(reports).isNotEmpty();

        // Проверяем, что метод processHierarchicalData возвращает те же данные, что и
        // executeRecursiveCte
        List<CategoryHierarchyReport> recursiveReports = cteRepository.executeRecursiveCte();

        assertThat(reports.size()).isEqualTo(recursiveReports.size());

        // Проверяем корректность данных
        for (CategoryHierarchyReport report : reports) {
            assertThat(report.getLevel()).isGreaterThanOrEqualTo(0);
            assertThat(report.getFullPath()).isNotNull();
            assertThat(report.getIndentedName()).isNotNull();
        }
    }
}
