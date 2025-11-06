/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp170.DailySalesReport;
import ru.mentee.power.model.mp170.SalesPersonRanking;
import ru.mentee.power.repository.interfaces.WindowFunctionsRepository;

public class PostgresWindowFunctionsRepository implements WindowFunctionsRepository {

    private ApplicationConfig config;

    private final String SQL_EXECUTE_RANKING =
            """
    WITH salesperson_totals AS (
        SELECT
            sp.id,
            sp.name,
            r.name as region_name,
            COALESCE(SUM(st.amount), 0) as total_sales
        FROM mentee_power.sales_people sp
        JOIN mentee_power.regions r ON sp.region_id = r.id
        LEFT JOIN mentee_power.sales_transactions st ON sp.id = st.salesperson_id
        WHERE st.status = 'COMPLETED' OR st.status IS NULL
        GROUP BY sp.id, sp.name, r.name
        HAVING COALESCE(SUM(st.amount), 0) > 0
    )
    SELECT
        id,
        name,
        region_name,
        total_sales,
        RANK() OVER (PARTITION BY region_name ORDER BY total_sales DESC) as region_rank,
        DENSE_RANK() OVER (PARTITION BY region_name ORDER BY total_sales DESC) as dense_rank,
        ROW_NUMBER() OVER (PARTITION BY region_name ORDER BY total_sales DESC) as row_number,
        0.0 as market_share_percent
    FROM salesperson_totals
    ORDER BY region_name, total_sales DESC;
    """;

    private final String SQL_CALCULATE_RUNNING_TOTALS =
            """
    WITH daily_sales_data AS (
        SELECT
            st.sale_date,
            COALESCE(SUM(st.amount), 0) as daily_sales,
            COUNT(*) as transaction_count,
            AVG(st.amount) as avg_transaction_amount
        FROM mentee_power.sales_transactions st
        WHERE st.status = 'COMPLETED'
        AND st.sale_date >= CURRENT_DATE - INTERVAL '30 days'
        GROUP BY st.sale_date
    )
    SELECT
        sale_date,
        daily_sales,
        SUM(daily_sales) OVER (ORDER BY sale_date) as cumulative_sales,
        transaction_count,
        avg_transaction_amount,
        0.0 as growth_percent
    FROM daily_sales_data
    ORDER BY sale_date;
    """;

    private final String SQL_ANALYZE_REGIONAL_PERFORMANCE =
            """
    WITH sales_by_salesperson AS (
        SELECT
            sp.id,
            sp.name,
            r.name as region_name,
            COALESCE(SUM(st.amount), 0) as total_sales
        FROM mentee_power.sales_people sp
        JOIN mentee_power.regions r ON sp.region_id = r.id
        LEFT JOIN mentee_power.sales_transactions st ON sp.id = st.salesperson_id
        WHERE st.status = 'COMPLETED' OR st.status IS NULL
        GROUP BY sp.id, sp.name, r.name
    ),
    regional_averages AS (
        SELECT
            id,
            name,
            region_name,
            total_sales,
            AVG(total_sales) OVER (PARTITION BY region_name) as region_avg_sales
        FROM sales_by_salesperson
    )
    SELECT
        id,
        name,
        region_name,
        total_sales,
        RANK() OVER (PARTITION BY region_name ORDER BY total_sales DESC) as region_rank,
        DENSE_RANK() OVER (PARTITION BY region_name ORDER BY total_sales DESC) as dense_rank,
        ROW_NUMBER() OVER (PARTITION BY region_name ORDER BY total_sales DESC) as row_number,
        CASE
            WHEN region_avg_sales > 0 THEN
                (total_sales / region_avg_sales * 100.0)
            ELSE 0.0
        END as market_share_percent
    FROM regional_averages
    WHERE total_sales > 0
    ORDER BY region_name, total_sales DESC;
    """;

    private final String SQL_GENERATE_QUARTILE_DISTRIBUTION =
            """
    WITH sales_by_salesperson AS (
        SELECT
            sp.id,
            sp.name,
            r.name as region_name,
            COALESCE(SUM(st.amount), 0) as total_sales
        FROM mentee_power.sales_people sp
        JOIN mentee_power.regions r ON sp.region_id = r.id
        LEFT JOIN mentee_power.sales_transactions st ON sp.id = st.salesperson_id
        WHERE st.status = 'COMPLETED' OR st.status IS NULL
        GROUP BY sp.id, sp.name, r.name
        HAVING COALESCE(SUM(st.amount), 0) > 0
    ),
    quartiled_sales AS (
        SELECT
            id,
            name,
            region_name,
            total_sales,
            NTILE(4) OVER (ORDER BY total_sales DESC) as quartile
        FROM sales_by_salesperson
    )
    SELECT
        id,
        name,
        region_name,
        total_sales,
        RANK() OVER (ORDER BY total_sales DESC) as region_rank,
        DENSE_RANK() OVER (ORDER BY total_sales DESC) as dense_rank,
        ROW_NUMBER() OVER (ORDER BY total_sales DESC) as row_number,
        0.0 as market_share_percent
    FROM quartiled_sales
    WHERE quartile = ?
    ORDER BY total_sales DESC;
    """;

    public PostgresWindowFunctionsRepository(ApplicationConfig config) {
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
    public List<SalesPersonRanking> executeRankingQuery() throws DataAccessException {
        List<SalesPersonRanking> rankings = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(SQL_EXECUTE_RANKING);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Long id = resultSet.getLong("id");
                String name = resultSet.getString("name");
                String regionName = resultSet.getString("region_name");
                BigDecimal totalSales = resultSet.getBigDecimal("total_sales");
                if (totalSales == null) {
                    totalSales = BigDecimal.ZERO;
                }
                Integer regionRank = resultSet.getInt("region_rank");
                if (resultSet.wasNull()) {
                    regionRank = null;
                }
                Integer denseRank = resultSet.getInt("dense_rank");
                if (resultSet.wasNull()) {
                    denseRank = null;
                }
                Integer rowNumber = resultSet.getInt("row_number");
                if (resultSet.wasNull()) {
                    rowNumber = null;
                }
                BigDecimal marketSharePercent = resultSet.getBigDecimal("market_share_percent");
                if (marketSharePercent == null) {
                    marketSharePercent = BigDecimal.ZERO;
                }

                SalesPersonRanking ranking =
                        SalesPersonRanking.builder()
                                .id(id)
                                .name(name)
                                .regionName(regionName)
                                .totalSales(totalSales)
                                .regionRank(regionRank)
                                .denseRank(denseRank)
                                .rowNumber(rowNumber)
                                .marketSharePercent(marketSharePercent)
                                .build();

                rankings.add(ranking);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка ранжирования продавцов", ex);
        }

        return rankings;
    }

    @Override
    public List<DailySalesReport> calculateRunningTotals() throws DataAccessException {
        List<DailySalesReport> reports = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(SQL_CALCULATE_RUNNING_TOTALS);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                java.sql.Date saleDateSql = resultSet.getDate("sale_date");
                LocalDate saleDate = saleDateSql != null ? saleDateSql.toLocalDate() : null;

                BigDecimal dailySales = resultSet.getBigDecimal("daily_sales");
                if (dailySales == null) {
                    dailySales = BigDecimal.ZERO;
                }

                BigDecimal cumulativeSales = resultSet.getBigDecimal("cumulative_sales");
                if (cumulativeSales == null) {
                    cumulativeSales = BigDecimal.ZERO;
                }

                Integer transactionCount = resultSet.getInt("transaction_count");
                if (resultSet.wasNull()) {
                    transactionCount = 0;
                }

                BigDecimal avgTransactionAmount = resultSet.getBigDecimal("avg_transaction_amount");

                BigDecimal growthPercent = resultSet.getBigDecimal("growth_percent");
                if (growthPercent == null) {
                    growthPercent = BigDecimal.ZERO;
                }

                DailySalesReport report =
                        DailySalesReport.builder()
                                .saleDate(saleDate)
                                .dailySales(dailySales)
                                .cumulativeSales(cumulativeSales)
                                .transactionCount(transactionCount)
                                .avgTransactionAmount(avgTransactionAmount)
                                .growthPercent(growthPercent)
                                .build();

                reports.add(report);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка вычисления накопительных сумм", ex);
        }

        return reports;
    }

    @Override
    public List<SalesPersonRanking> analyzeRegionalPerformance() throws DataAccessException {
        List<SalesPersonRanking> rankings = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(SQL_ANALYZE_REGIONAL_PERFORMANCE);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Long id = resultSet.getLong("id");
                String name = resultSet.getString("name");
                String regionName = resultSet.getString("region_name");
                BigDecimal totalSales = resultSet.getBigDecimal("total_sales");
                if (totalSales == null) {
                    totalSales = BigDecimal.ZERO;
                }
                Integer regionRank = resultSet.getInt("region_rank");
                if (resultSet.wasNull()) {
                    regionRank = null;
                }
                Integer denseRank = resultSet.getInt("dense_rank");
                if (resultSet.wasNull()) {
                    denseRank = null;
                }
                Integer rowNumber = resultSet.getInt("row_number");
                if (resultSet.wasNull()) {
                    rowNumber = null;
                }
                BigDecimal marketSharePercent = resultSet.getBigDecimal("market_share_percent");
                if (marketSharePercent == null) {
                    marketSharePercent = BigDecimal.ZERO;
                }

                SalesPersonRanking ranking =
                        SalesPersonRanking.builder()
                                .id(id)
                                .name(name)
                                .regionName(regionName)
                                .totalSales(totalSales)
                                .regionRank(regionRank)
                                .denseRank(denseRank)
                                .rowNumber(rowNumber)
                                .marketSharePercent(marketSharePercent)
                                .build();

                rankings.add(ranking);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка анализа региональной производительности", ex);
        }

        return rankings;
    }

    @Override
    public List<SalesPersonRanking> generateQuartileDistribution(Integer quartileNumber)
            throws DataAccessException {
        List<SalesPersonRanking> rankings = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(SQL_GENERATE_QUARTILE_DISTRIBUTION)) {
            statement.setInt(1, quartileNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Long id = resultSet.getLong("id");
                    String name = resultSet.getString("name");
                    String regionName = resultSet.getString("region_name");
                    BigDecimal totalSales = resultSet.getBigDecimal("total_sales");
                    if (totalSales == null) {
                        totalSales = BigDecimal.ZERO;
                    }
                    Integer regionRank = resultSet.getInt("region_rank");
                    if (resultSet.wasNull()) {
                        regionRank = null;
                    }
                    Integer denseRank = resultSet.getInt("dense_rank");
                    if (resultSet.wasNull()) {
                        denseRank = null;
                    }
                    Integer rowNumber = resultSet.getInt("row_number");
                    if (resultSet.wasNull()) {
                        rowNumber = null;
                    }
                    BigDecimal marketSharePercent = resultSet.getBigDecimal("market_share_percent");
                    if (marketSharePercent == null) {
                        marketSharePercent = BigDecimal.ZERO;
                    }

                    SalesPersonRanking ranking =
                            SalesPersonRanking.builder()
                                    .id(id)
                                    .name(name)
                                    .regionName(regionName)
                                    .totalSales(totalSales)
                                    .regionRank(regionRank)
                                    .denseRank(denseRank)
                                    .rowNumber(rowNumber)
                                    .marketSharePercent(marketSharePercent)
                                    .build();

                    rankings.add(ranking);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Ошибка генерации распределения по квартилям", ex);
        }

        return rankings;
    }
}
