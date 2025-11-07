/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp171.AbcAnalysisReport;
import ru.mentee.power.model.mp171.CategoryHierarchyReport;
import ru.mentee.power.model.mp171.CohortAnalysisReport;
import ru.mentee.power.model.mp171.CustomerSegmentReport;
import ru.mentee.power.model.mp171.ProductTrendReport;
import ru.mentee.power.repository.interfaces.CteAnalyticsRepository;

/**
 * Реализация репозитория для выполнения CTE запросов в PostgreSQL.
 * Использует CteQueryBuilder для построения запросов и выполняет их через JDBC.
 */
public class PostgresCteAnalyticsRepository implements CteAnalyticsRepository {

    private ApplicationConfig config;

    public PostgresCteAnalyticsRepository(ApplicationConfig config) {
        this.config = config;
    }

    protected Connection getConnection() throws SQLException, DataAccessException {
        Connection connection =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (PreparedStatement statement =
                connection.prepareStatement("SET search_path TO mentee_power, public")) {
            statement.execute();
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка подключения", ex);
        }
        return connection;
    }

    @Override
    public List<CustomerSegmentReport> executeMultipleCte() throws DataAccessException {
        List<CustomerSegmentReport> reports = new ArrayList<>();
        String sql = CteQueryBuilder.buildSegmentationCte();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CustomerSegmentReport report =
                        CustomerSegmentReport.builder()
                                .segment(resultSet.getString("segment"))
                                .activityStatus(resultSet.getString("activity_status"))
                                .customersCount(resultSet.getInt("customers_count"))
                                .avgTotalSpent(resultSet.getBigDecimal("avg_total_spent"))
                                .avgOrderValue(resultSet.getBigDecimal("avg_order_value"))
                                .segmentRevenue(resultSet.getBigDecimal("segment_revenue"))
                                .revenueSharePercent(
                                        resultSet.getBigDecimal("revenue_share_percent"))
                                .build();
                reports.add(report);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка выполнения запроса сегментации клиентов", e);
        }

        return reports;
    }

    @Override
    public List<CategoryHierarchyReport> executeRecursiveCte() throws DataAccessException {
        return processHierarchicalData();
    }

    @Override
    public List<CategoryHierarchyReport> processHierarchicalData() throws DataAccessException {
        List<CategoryHierarchyReport> reports = new ArrayList<>();
        String sql = CteQueryBuilder.buildRecursiveHierarchy();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CategoryHierarchyReport report =
                        CategoryHierarchyReport.builder()
                                .indentedName(resultSet.getString("indented_name"))
                                .fullPath(resultSet.getString("full_path"))
                                .level(resultSet.getInt("level"))
                                .productsCount(resultSet.getInt("products_count"))
                                .ordersCount(resultSet.getInt("orders_count"))
                                .totalRevenue(resultSet.getBigDecimal("total_revenue"))
                                .rootCategoryRevenue(
                                        resultSet.getBigDecimal("root_category_revenue"))
                                .build();
                reports.add(report);
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Ошибка выполнения рекурсивного запроса иерархии категорий", e);
        }

        return reports;
    }

    @Override
    public List<CohortAnalysisReport> performCohortAnalysis() throws DataAccessException {
        List<CohortAnalysisReport> reports = new ArrayList<>();
        String sql = CteQueryBuilder.buildCohortAnalysis();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Date cohortMonthDate = resultSet.getDate("cohort_month");
                LocalDate cohortMonth =
                        cohortMonthDate != null ? cohortMonthDate.toLocalDate() : null;

                CohortAnalysisReport report =
                        CohortAnalysisReport.builder()
                                .cohortMonth(cohortMonth)
                                .cohortSize(resultSet.getInt("cohort_size"))
                                .monthNumber(resultSet.getInt("month_number"))
                                .activeCustomers(resultSet.getInt("active_customers"))
                                .retentionRate(resultSet.getBigDecimal("retention_rate"))
                                .avgOrderValue(resultSet.getBigDecimal("avg_order_value"))
                                .cohortRevenue(resultSet.getBigDecimal("cohort_revenue"))
                                .build();
                reports.add(report);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка выполнения когортного анализа", e);
        }

        return reports;
    }

    @Override
    public List<AbcAnalysisReport> performAbcAnalysis() throws DataAccessException {
        List<AbcAnalysisReport> reports = new ArrayList<>();
        String sql = CteQueryBuilder.buildAbcAnalysis();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                AbcAnalysisReport report =
                        AbcAnalysisReport.builder()
                                .productId(resultSet.getLong("product_id"))
                                .productName(resultSet.getString("product_name"))
                                .categoryName(resultSet.getString("category_name"))
                                .totalRevenue(resultSet.getBigDecimal("total_revenue"))
                                .cumulativeRevenue(resultSet.getBigDecimal("cumulative_revenue"))
                                .cumulativePercent(resultSet.getBigDecimal("cumulative_percent"))
                                .abcCategory(resultSet.getString("abc_category"))
                                .revenueRank(resultSet.getInt("revenue_rank"))
                                .build();
                reports.add(report);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка выполнения ABC анализа", e);
        }

        return reports;
    }

    @Override
    public List<ProductTrendReport> performProductTrendsAnalysis() throws DataAccessException {
        List<ProductTrendReport> reports = new ArrayList<>();
        String sql = CteQueryBuilder.buildProductTrends();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Date salesMonthDate = resultSet.getDate("sales_month");
                LocalDate salesMonth = salesMonthDate != null ? salesMonthDate.toLocalDate() : null;

                ProductTrendReport report =
                        ProductTrendReport.builder()
                                .productName(resultSet.getString("product_name"))
                                .categoryName(resultSet.getString("category_name"))
                                .salesMonth(salesMonth)
                                .revenue(resultSet.getBigDecimal("revenue"))
                                .unitsSold(resultSet.getInt("units_sold"))
                                .revenueGrowthPercent(
                                        resultSet.getBigDecimal("revenue_growth_percent"))
                                .unitsGrowthPercent(resultSet.getBigDecimal("units_growth_percent"))
                                .trendCategory(resultSet.getString("trend_category"))
                                .build();
                reports.add(report);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка выполнения анализа трендов продуктов", e);
        }

        return reports;
    }
}
