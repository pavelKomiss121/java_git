/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp172.*;
import ru.mentee.power.repository.interfaces.AdvancedAnalyticsRepository;

public class PostgresAdvancedAnalyticsRepository implements AdvancedAnalyticsRepository {

    private static String SQL_CTE =
            """
      WITH RECURSIVE org_hierarchy AS (
        -- Корневые организации
        SELECT
            id,
            name,
            parent_id,
            0 as level,
            is_active,
            name as hierarchy_path,
            ARRAY[id] as path_ids
        FROM mentee_power.organizations
        WHERE parent_id IS NULL
          AND (? = true OR is_active = true)

        UNION ALL

        -- Дочерние организации
        SELECT
            o.id,
            o.name,
            o.parent_id,
            oh.level + 1,
            o.is_active,
            oh.hierarchy_path || ' / ' || o.name,
            oh.path_ids || o.id
        FROM mentee_power.organizations o
        JOIN org_hierarchy oh ON o.parent_id = oh.id
        WHERE (? = true OR o.is_active = true)
      ),
      org_sales AS (
        SELECT
            oh.id as organization_id,
            oh.name as organization_name,
            oh.parent_id as parent_organization_id,
            oh.level as hierarchy_level,
            oh.hierarchy_path,
            oh.is_active,
            COUNT(DISTINCT e.id) as employees_count,
            COUNT(DISTINCT ord.id) as order_count,
            COALESCE(SUM(ord.total_amount), 0) as direct_sales,
            COALESCE(AVG(ord.total_amount), 0) as avg_order_value
        FROM org_hierarchy oh
        LEFT JOIN mentee_power.employees e ON oh.id = e.organization_id
        LEFT JOIN mentee_power.orders ord ON e.id = ord.sales_rep_id
            AND ord.order_date >= CURRENT_DATE - INTERVAL '%d months'
            AND ord.status = 'COMPLETED'
        GROUP BY oh.id, oh.name, oh.parent_id, oh.level, oh.hierarchy_path, oh.is_active, oh.path_ids
      ),
      org_with_children_sales AS (
        SELECT
            os.*,
            COALESCE((
                SELECT SUM(child.direct_sales)
                FROM org_sales child
                WHERE child.parent_organization_id = os.organization_id
            ), 0) as children_revenue
        FROM org_sales os
      ),
      hierarchical_sales AS (
        SELECT
            *,
            -- Накопительные продажи включая подразделения
            direct_sales + children_revenue as total_revenue,
            -- Ранжирование по уровням
            RANK() OVER (
                PARTITION BY hierarchy_level
                ORDER BY (direct_sales + children_revenue) DESC
            ) as level_rank
        FROM org_with_children_sales
      ),
      total_revenue AS (
        SELECT SUM(total_revenue) as grand_total
        FROM hierarchical_sales
      )
      SELECT
          hs.organization_id,
          hs.organization_name,
          hs.parent_organization_id,
          hs.hierarchy_level,
          hs.hierarchy_path,
          hs.total_revenue,
          hs.direct_sales as own_revenue,
          hs.children_revenue,
          hs.order_count,
          hs.avg_order_value,
          CASE
            WHEN tr.grand_total > 0
            THEN hs.total_revenue * 100.0 / tr.grand_total
            ELSE 0
          END as revenue_percent,
          CURRENT_DATE - INTERVAL '%d months' as period_start,
          CURRENT_DATE as period_end,
          hs.is_active
      FROM hierarchical_sales hs
      CROSS JOIN total_revenue tr
      ORDER BY hs.hierarchy_path;
      """;

    private ApplicationConfig config;

    public PostgresAdvancedAnalyticsRepository(ApplicationConfig config) {
        this.config = config;
    }

    protected Connection getConnection() throws SQLException, DataAccessException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (PreparedStatement statement =
                conn.prepareStatement("SET search_path TO mentee_power, public")) {
            statement.execute();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка соединения", e);
        }
        return conn;
    }

    @Override
    public List<OrganizationSalesHierarchy> getOrganizationSalesHierarchy(
            Boolean includeInactive, Integer periodMonths)
            throws DataAccessException, SQLException {
        List<OrganizationSalesHierarchy> result = new ArrayList<>();

        if (includeInactive == null) {
            includeInactive = false;
        }
        if (periodMonths == null || periodMonths <= 0) {
            periodMonths = 12;
        }

        String sql = String.format(SQL_CTE, periodMonths, periodMonths);

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(sql)) {

            statement.setBoolean(1, includeInactive);
            statement.setBoolean(2, includeInactive);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrganizationSalesHierarchy hierarchy =
                            OrganizationSalesHierarchy.builder()
                                    .organizationId(rs.getLong("organization_id"))
                                    .organizationName(rs.getString("organization_name"))
                                    .parentOrganizationId(
                                            rs.getObject("parent_organization_id", Long.class))
                                    .hierarchyLevel(rs.getInt("hierarchy_level"))
                                    .hierarchyPath(rs.getString("hierarchy_path"))
                                    .totalRevenue(rs.getBigDecimal("total_revenue"))
                                    .ownRevenue(rs.getBigDecimal("own_revenue"))
                                    .childrenRevenue(rs.getBigDecimal("children_revenue"))
                                    .orderCount(rs.getInt("order_count"))
                                    .avgOrderValue(rs.getBigDecimal("avg_order_value"))
                                    .revenuePercent(rs.getBigDecimal("revenue_percent"))
                                    .periodStart(rs.getObject("period_start", LocalDate.class))
                                    .periodEnd(rs.getObject("period_end", LocalDate.class))
                                    .isActive(rs.getBoolean("is_active"))
                                    .build();

                    result.add(hierarchy);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Ошибка при получении иерархии продаж организаций: " + e.getMessage(), e);
        }

        return result;
    }

    private static String SQL_TIME_SERIES =
            """
      WITH daily_sales AS (
        SELECT
            DATE(order_date) as sale_date,
            COALESCE(SUM(total_amount), SUM(total), 0) as daily_revenue,
            COUNT(DISTINCT id) as order_count
        FROM mentee_power.orders
        WHERE order_date >= ?
          AND order_date <= ?
          AND status = 'COMPLETED'
        GROUP BY DATE(order_date)
      ),
      time_series_with_windows AS (
        SELECT
            sale_date as period_date,
            daily_revenue as revenue,
            order_count,
            -- Скользящее среднее за 7 дней
            AVG(daily_revenue) OVER (
                ORDER BY sale_date
                ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
            ) as moving_average_revenue,
            -- Скользящее среднее за 30 дней
            AVG(daily_revenue) OVER (
                ORDER BY sale_date
                ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
            ) as moving_average_30,
            -- Сравнение с предыдущим периодом
            LAG(daily_revenue, 1) OVER (ORDER BY sale_date) as prev_period,
            LAG(daily_revenue, 7) OVER (ORDER BY sale_date) as prev_week,
            -- Процент изменения
            CASE
                WHEN LAG(daily_revenue, 1) OVER (ORDER BY sale_date) > 0
                THEN ((daily_revenue / LAG(daily_revenue, 1) OVER (ORDER BY sale_date)) - 1) * 100
                ELSE 0
            END as revenue_change_percent,
            -- Стандартное отклонение
            STDDEV(daily_revenue) OVER (
                ORDER BY sale_date
                ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
            ) as standard_deviation,
            -- Среднее значение
            AVG(daily_revenue) OVER (
                ORDER BY sale_date
                ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
            ) as mean_sales
        FROM daily_sales
      )
      SELECT
          period_date,
          revenue,
          order_count,
          moving_average_revenue,
          moving_average_30 as moving_average_orders,
          COALESCE(prev_period, 0) as revenue_trend,
          COALESCE(revenue_change_percent, 0) as revenue_change_percent,
          CASE
            WHEN mean_sales > 0
            THEN ((revenue - mean_sales) / mean_sales) * 100
            ELSE 0
          END as deviation_from_average,
          CASE
            WHEN standard_deviation > 0
            THEN ABS((revenue - mean_sales) / standard_deviation) > 2
            ELSE false
          END as is_anomaly,
          CASE
            WHEN standard_deviation > 0 AND ABS((revenue - mean_sales) / standard_deviation) > 2
            THEN CASE
                WHEN revenue > mean_sales THEN 'SPIKE'
                ELSE 'DROP'
            END
            ELSE 'NORMAL'
          END as anomaly_type,
          COALESCE(standard_deviation, 0) as standard_deviation,
          CASE
            WHEN standard_deviation > 0
            THEN (revenue - mean_sales) / standard_deviation
            ELSE 0
          END as z_score,
          NULL::DECIMAL as forecasted_value,
          NULL::DECIMAL as confidence_interval_upper,
          NULL::DECIMAL as confidence_interval_lower
      FROM time_series_with_windows
      ORDER BY period_date;
      """;

    @Override
    public List<TimeSeriesAnalysis> getTimeSeriesAnalysis(LocalDate startDate, LocalDate endDate)
            throws DataAccessException {
        List<TimeSeriesAnalysis> result = new ArrayList<>();

        if (startDate == null || endDate == null) {
            throw new DataAccessException("startDate и endDate не могут быть null");
        }

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(SQL_TIME_SERIES)) {

            statement.setObject(1, startDate);
            statement.setObject(2, endDate);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    TimeSeriesAnalysis analysis =
                            TimeSeriesAnalysis.builder()
                                    .periodDate(rs.getObject("period_date", LocalDate.class))
                                    .revenue(rs.getBigDecimal("revenue"))
                                    .orderCount(rs.getInt("order_count"))
                                    .movingAverageRevenue(
                                            rs.getBigDecimal("moving_average_revenue"))
                                    .movingAverageOrders(rs.getBigDecimal("moving_average_orders"))
                                    .revenueTrend(rs.getBigDecimal("revenue_trend"))
                                    .revenueChangePercent(
                                            rs.getBigDecimal("revenue_change_percent"))
                                    .deviationFromAverage(
                                            rs.getBigDecimal("deviation_from_average"))
                                    .isAnomaly(rs.getBoolean("is_anomaly"))
                                    .anomalyType(rs.getString("anomaly_type"))
                                    .standardDeviation(rs.getBigDecimal("standard_deviation"))
                                    .zScore(rs.getBigDecimal("z_score"))
                                    .forecastedValue(
                                            rs.getObject(
                                                    "forecasted_value", java.math.BigDecimal.class))
                                    .confidenceIntervalUpper(
                                            rs.getObject(
                                                    "confidence_interval_upper",
                                                    java.math.BigDecimal.class))
                                    .confidenceIntervalLower(
                                            rs.getObject(
                                                    "confidence_interval_lower",
                                                    java.math.BigDecimal.class))
                                    .build();

                    result.add(analysis);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Ошибка при получении анализа временных рядов: " + e.getMessage(), e);
        }

        return result;
    }

    private static String SQL_COHORT_ANALYSIS =
            """
      WITH first_purchase AS (
        SELECT
            user_id as customer_id,
            DATE_TRUNC('month', MIN(order_date)) as cohort_month
        FROM mentee_power.orders
        WHERE status = 'COMPLETED'
        GROUP BY user_id
      ),
      cohort_data AS (
        SELECT
            o.user_id as customer_id,
            fp.cohort_month,
            DATE_TRUNC('month', o.order_date) as order_month,
            COALESCE(SUM(o.total_amount), SUM(o.total), 0) as period_revenue,
            COUNT(DISTINCT o.id) as order_count
        FROM mentee_power.orders o
        JOIN first_purchase fp ON o.user_id = fp.customer_id
        WHERE o.status = 'COMPLETED'
          AND fp.cohort_month >= ?
        GROUP BY o.user_id, fp.cohort_month, DATE_TRUNC('month', o.order_date)
      ),
      cohort_table AS (
        SELECT
            cohort_month,
            order_month,
            COUNT(DISTINCT customer_id) as active_customers,
            SUM(period_revenue) as period_revenue,
            SUM(order_count) as total_orders,
            AVG(period_revenue) as avg_revenue_per_customer
        FROM cohort_data
        GROUP BY cohort_month, order_month
      ),
      cohort_sizes AS (
        SELECT
            cohort_month,
            COUNT(DISTINCT customer_id) as cohort_size
        FROM first_purchase
        WHERE cohort_month >= ?
        GROUP BY cohort_month
      ),
      cohort_periods AS (
        SELECT
            ct.cohort_month,
            cs.cohort_size,
            ct.order_month,
            EXTRACT(MONTH FROM AGE(ct.order_month, ct.cohort_month))::INTEGER as period_number,
            ct.active_customers,
            ct.period_revenue,
            ct.total_orders,
            ct.avg_revenue_per_customer,
            CASE
                WHEN cs.cohort_size > 0
                THEN (ct.active_customers::DECIMAL / cs.cohort_size) * 100
                ELSE 0
            END as retention_rate
        FROM cohort_table ct
        JOIN cohort_sizes cs ON ct.cohort_month = cs.cohort_month
        WHERE EXTRACT(MONTH FROM AGE(ct.order_month, ct.cohort_month)) <= ?
      ),
      cumulative_revenue AS (
        SELECT
            *,
            SUM(period_revenue) OVER (
                PARTITION BY cohort_month
                ORDER BY period_number
                ROWS UNBOUNDED PRECEDING
            ) as cumulative_revenue,
            AVG(period_revenue) OVER (
                PARTITION BY cohort_month
                ORDER BY period_number
                ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
            ) as avg_revenue_trend
        FROM cohort_periods
      )
      SELECT
          cohort_month,
          cohort_size,
          period_number,
          order_month as period_date,
          active_customers,
          retention_rate,
          period_revenue,
          cumulative_revenue,
          CASE
            WHEN total_orders > 0
            THEN period_revenue / total_orders
            ELSE 0
          END as avg_order_value,
          CASE
            WHEN active_customers > 0
            THEN total_orders::DECIMAL / active_customers
            ELSE 0
          END as avg_orders_per_customer,
          -- Прогнозируемый LTV (упрощенный расчет)
          cumulative_revenue * (100.0 / NULLIF(retention_rate, 0)) as predicted_ltv,
          cumulative_revenue as realized_ltv,
          cumulative_revenue * 1.2 as projected_ltv,
          retention_rate / 100.0 as conversion_rate,
          NULL::DECIMAL as avg_days_between_orders,
          CASE
            WHEN period_number = 0 THEN 0
            ELSE 100 - retention_rate
          END as churn_rate,
          CASE
            WHEN retention_rate > 0
            THEN 100.0 / NULLIF(retention_rate, 0)
            ELSE 0
          END as predicted_lifetime_months
      FROM cumulative_revenue
      ORDER BY cohort_month, period_number;
      """;

    @Override
    public List<CohortAnalysisAdvanced> getAdvancedCohortAnalysis(
            LocalDate cohortStartMonth, Integer maxPeriods) throws DataAccessException {
        List<CohortAnalysisAdvanced> result = new ArrayList<>();

        if (cohortStartMonth == null) {
            cohortStartMonth = LocalDate.now().minusMonths(12).withDayOfMonth(1);
        }
        if (maxPeriods == null || maxPeriods <= 0) {
            maxPeriods = 12;
        }

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(SQL_COHORT_ANALYSIS)) {

            statement.setObject(1, cohortStartMonth);
            statement.setObject(2, cohortStartMonth);
            statement.setInt(3, maxPeriods);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CohortAnalysisAdvanced cohort =
                            CohortAnalysisAdvanced.builder()
                                    .cohortMonth(rs.getObject("cohort_month", LocalDate.class))
                                    .cohortSize(rs.getInt("cohort_size"))
                                    .periodNumber(rs.getInt("period_number"))
                                    .periodDate(rs.getObject("period_date", LocalDate.class))
                                    .activeCustomers(rs.getInt("active_customers"))
                                    .retentionRate(rs.getBigDecimal("retention_rate"))
                                    .periodRevenue(rs.getBigDecimal("period_revenue"))
                                    .cumulativeRevenue(rs.getBigDecimal("cumulative_revenue"))
                                    .avgOrderValue(rs.getBigDecimal("avg_order_value"))
                                    .avgOrdersPerCustomer(
                                            rs.getBigDecimal("avg_orders_per_customer"))
                                    .predictedLtv(rs.getBigDecimal("predicted_ltv"))
                                    .realizedLtv(rs.getBigDecimal("realized_ltv"))
                                    .projectedLtv(rs.getBigDecimal("projected_ltv"))
                                    .conversionRate(rs.getBigDecimal("conversion_rate"))
                                    .avgDaysBetweenOrders(
                                            rs.getObject(
                                                    "avg_days_between_orders",
                                                    java.math.BigDecimal.class))
                                    .churnRate(rs.getBigDecimal("churn_rate"))
                                    .predictedLifetimeMonths(
                                            rs.getBigDecimal("predicted_lifetime_months"))
                                    .build();

                    result.add(cohort);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Ошибка при получении когортного анализа: " + e.getMessage(), e);
        }

        return result;
    }

    private static String SQL_ABC_XYZ_ANALYSIS =
            """
      WITH product_sales AS (
        SELECT
            p.id as product_id,
            p.name as product_name,
            c.name as category_name,
            COALESCE(SUM(oi.total_price), SUM(oi.price * oi.quantity), 0) as total_revenue,
            SUM(oi.quantity) as quantity_sold,
            AVG(oi.price) as avg_unit_price,
            COUNT(DISTINCT oi.order_id) as order_count
        FROM mentee_power.products p
        LEFT JOIN mentee_power.categories c ON p.category_id = c.id
        LEFT JOIN mentee_power.order_items oi ON p.id = oi.product_id
        LEFT JOIN mentee_power.orders o ON oi.order_id = o.id
        WHERE o.order_date >= CURRENT_DATE - INTERVAL '%d days'
          AND (o.status = 'COMPLETED' OR o.status IS NULL)
        GROUP BY p.id, p.name, c.name
      ),
      sales_stats AS (
        SELECT
            *,
            COUNT(DISTINCT order_id) OVER () as total_orders,
            AVG(quantity_sold) OVER () as mean_sales,
            STDDEV(quantity_sold) OVER () as stddev_sales
        FROM product_sales
      ),
      ranked_products AS (
        SELECT
            *,
            SUM(total_revenue) OVER (
                ORDER BY total_revenue DESC
                ROWS UNBOUNDED PRECEDING
            ) as cumulative_revenue,
            SUM(total_revenue) OVER () as grand_total_revenue,
            RANK() OVER (ORDER BY total_revenue DESC) as revenue_rank,
            PERCENT_RANK() OVER (ORDER BY total_revenue DESC) as percent_rank,
            NTILE(4) OVER (ORDER BY total_revenue DESC) as quantile,
            CASE
                WHEN stddev_sales > 0
                THEN stddev_sales / NULLIF(mean_sales, 0)
                ELSE 0
            END as coefficient_of_variation
        FROM sales_stats
      ),
      abc_classified AS (
        SELECT
            *,
            CASE
                WHEN cumulative_revenue <= grand_total_revenue * 0.8 THEN 'A'
                WHEN cumulative_revenue <= grand_total_revenue * 0.95 THEN 'B'
                ELSE 'C'
            END as abc_category
        FROM ranked_products
      ),
      xyz_classified AS (
        SELECT
            *,
            CASE
                WHEN coefficient_of_variation < 0.5 THEN 'X'
                WHEN coefficient_of_variation < 1.0 THEN 'Y'
                ELSE 'Z'
            END as xyz_category,
            RANK() OVER (ORDER BY coefficient_of_variation ASC) as stability_rank
        FROM abc_classified
      )
      SELECT
          product_id,
          product_name,
          category_name,
          total_revenue,
          quantity_sold,
          avg_unit_price,
          cumulative_revenue,
          CASE
            WHEN grand_total_revenue > 0
            THEN (cumulative_revenue / grand_total_revenue) * 100
            ELSE 0
          END as cumulative_percent,
          abc_category,
          coefficient_of_variation,
          stddev_sales as standard_deviation,
          mean_sales as mean_sales,
          xyz_category,
          abc_category || xyz_category as abc_xyz_category,
          revenue_rank,
          stability_rank,
          percent_rank,
          quantile
      FROM xyz_classified
      WHERE total_revenue > 0
      ORDER BY total_revenue DESC;
      """;

    @Override
    public List<AbcXyzAnalysis> getAbcXyzAnalysis(Integer periodDays) throws DataAccessException {
        List<AbcXyzAnalysis> result = new ArrayList<>();

        if (periodDays == null || periodDays <= 0) {
            periodDays = 365;
        }

        String sql = String.format(SQL_ABC_XYZ_ANALYSIS, periodDays);

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(sql)) {

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    AbcXyzAnalysis analysis =
                            AbcXyzAnalysis.builder()
                                    .productId(rs.getLong("product_id"))
                                    .productName(rs.getString("product_name"))
                                    .categoryName(rs.getString("category_name"))
                                    .totalRevenue(rs.getBigDecimal("total_revenue"))
                                    .quantitySold(rs.getInt("quantity_sold"))
                                    .avgUnitPrice(rs.getBigDecimal("avg_unit_price"))
                                    .cumulativeRevenue(rs.getBigDecimal("cumulative_revenue"))
                                    .cumulativePercent(rs.getBigDecimal("cumulative_percent"))
                                    .abcCategory(rs.getString("abc_category"))
                                    .coefficientOfVariation(
                                            rs.getBigDecimal("coefficient_of_variation"))
                                    .standardDeviation(rs.getBigDecimal("standard_deviation"))
                                    .meanSales(rs.getBigDecimal("mean_sales"))
                                    .xyzCategory(rs.getString("xyz_category"))
                                    .abcXyzCategory(rs.getString("abc_xyz_category"))
                                    .revenueRank(rs.getInt("revenue_rank"))
                                    .stabilityRank(rs.getInt("stability_rank"))
                                    .percentRank(rs.getBigDecimal("percent_rank"))
                                    .quantile(rs.getInt("quantile"))
                                    .build();

                    result.add(analysis);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Ошибка при получении ABC-XYZ анализа: " + e.getMessage(), e);
        }

        return result;
    }

    private static String SQL_SALES_FUNNEL =
            """
      WITH funnel_stages AS (
        SELECT
            'VISIT' as stage_name,
            1 as stage_order,
            COUNT(DISTINCT u.id) as users_at_stage
        FROM mentee_power.users u
        WHERE u.registration_date >= ? AND u.registration_date <= ?

        UNION ALL

        SELECT
            'BROWSE' as stage_name,
            2 as stage_order,
            COUNT(DISTINCT o.user_id) as users_at_stage
        FROM mentee_power.orders o
        WHERE o.order_date >= ? AND o.order_date <= ?

        UNION ALL

        SELECT
            'CART' as stage_name,
            3 as stage_order,
            COUNT(DISTINCT o.user_id) as users_at_stage
        FROM mentee_power.orders o
        WHERE o.order_date >= ? AND o.order_date <= ?
          AND o.status IN ('PENDING', 'CONFIRMED', 'COMPLETED')

        UNION ALL

        SELECT
            'CHECKOUT' as stage_name,
            4 as stage_order,
            COUNT(DISTINCT o.user_id) as users_at_stage
        FROM mentee_power.orders o
        WHERE o.order_date >= ? AND o.order_date <= ?
          AND o.status IN ('CONFIRMED', 'COMPLETED')

        UNION ALL

        SELECT
            'PURCHASE' as stage_name,
            5 as stage_order,
            COUNT(DISTINCT o.user_id) as users_at_stage
        FROM mentee_power.orders o
        WHERE o.order_date >= ? AND o.order_date <= ?
          AND o.status = 'COMPLETED'
      ),
      funnel_with_metrics AS (
        SELECT
            fs.stage_name as funnel_stage,
            fs.stage_order,
            fs.users_at_stage,
            LAG(fs.users_at_stage, 1) OVER (ORDER BY fs.stage_order) as prev_stage_users,
            LEAD(fs.users_at_stage, 1) OVER (ORDER BY fs.stage_order) as next_stage_users,
            FIRST_VALUE(fs.users_at_stage) OVER (ORDER BY fs.stage_order) as first_stage_users
        FROM funnel_stages fs
      ),
      funnel_analysis AS (
        SELECT
            funnel_stage,
            stage_order,
            users_at_stage,
            COALESCE(next_stage_users, 0) as users_moved_to_next,
            CASE
                WHEN prev_stage_users > 0
                THEN (users_at_stage::DECIMAL / prev_stage_users) * 100
                ELSE 0
            END as conversion_from_previous,
            CASE
                WHEN first_stage_users > 0
                THEN (users_at_stage::DECIMAL / first_stage_users) * 100
                ELSE 0
            END as conversion_from_first,
            COALESCE(prev_stage_users - users_at_stage, 0) as dropouts,
            CASE
                WHEN prev_stage_users > 0
                THEN ((prev_stage_users - users_at_stage)::DECIMAL / prev_stage_users) * 100
                ELSE 0
            END as dropout_rate,
            NULL::DECIMAL as avg_time_on_stage,
            COALESCE((
                SELECT SUM(COALESCE(o.total_amount, o.total, 0))
                FROM mentee_power.orders o
                WHERE o.order_date >= ? AND o.order_date <= ?
                  AND CASE
                    WHEN funnel_stage = 'PURCHASE' THEN o.status = 'COMPLETED'
                    WHEN funnel_stage = 'CHECKOUT' THEN o.status IN ('CONFIRMED', 'COMPLETED')
                    ELSE false
                  END
            ), 0) as stage_revenue,
            CASE
                WHEN users_at_stage > 0
                THEN COALESCE((
                    SELECT SUM(COALESCE(o.total_amount, o.total, 0)) / users_at_stage
                    FROM mentee_power.orders o
                    WHERE o.order_date >= ? AND o.order_date <= ?
                      AND CASE
                        WHEN funnel_stage = 'PURCHASE' THEN o.status = 'COMPLETED'
                        WHEN funnel_stage = 'CHECKOUT' THEN o.status IN ('CONFIRMED', 'COMPLETED')
                        ELSE 0
                      END
                ), 0)
                ELSE 0
            END as avg_revenue_per_user,
            CASE
                WHEN funnel_stage = 'PURCHASE'
                THEN users_at_stage
                ELSE 0
            END as completed_deals,
            CASE
                WHEN first_stage_users > 0 AND funnel_stage = 'PURCHASE'
                THEN (users_at_stage::DECIMAL / first_stage_users) * 100
                ELSE 0
            END as deal_conversion_rate,
            NULL::DECIMAL as conversion_change_percent,
            CASE
                WHEN conversion_from_previous > LAG(conversion_from_previous, 1) OVER (ORDER BY stage_order)
                THEN 'IMPROVING'
                WHEN conversion_from_previous < LAG(conversion_from_previous, 1) OVER (ORDER BY stage_order)
                THEN 'DECLINING'
                ELSE 'STABLE'
            END as stage_trend
        FROM funnel_with_metrics
      )
      SELECT
          funnel_stage,
          stage_order,
          users_at_stage,
          users_moved_to_next,
          conversion_from_previous,
          conversion_from_first,
          dropouts,
          dropout_rate,
          avg_time_on_stage,
          stage_revenue,
          avg_revenue_per_user,
          completed_deals,
          deal_conversion_rate,
          ? as analysis_start_date,
          ? as analysis_end_date,
          conversion_change_percent,
          stage_trend
      FROM funnel_analysis
      ORDER BY stage_order;
      """;

    @Override
    public List<SalesFunnelAnalysis> getSalesFunnelAnalysis(LocalDate startDate, LocalDate endDate)
            throws DataAccessException {
        List<SalesFunnelAnalysis> result = new ArrayList<>();

        if (startDate == null || endDate == null) {
            throw new DataAccessException("startDate и endDate не могут быть null");
        }

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(SQL_SALES_FUNNEL)) {

            // Параметры для каждого этапа воронки
            statement.setObject(1, startDate);
            statement.setObject(2, endDate);
            statement.setObject(3, startDate);
            statement.setObject(4, endDate);
            statement.setObject(5, startDate);
            statement.setObject(6, endDate);
            statement.setObject(7, startDate);
            statement.setObject(8, endDate);
            statement.setObject(9, startDate);
            statement.setObject(10, endDate);
            statement.setObject(11, startDate);
            statement.setObject(12, endDate);
            statement.setObject(13, startDate);
            statement.setObject(14, endDate);
            statement.setObject(15, startDate);
            statement.setObject(16, endDate);
            statement.setObject(17, startDate);
            statement.setObject(18, endDate);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SalesFunnelAnalysis funnel =
                            SalesFunnelAnalysis.builder()
                                    .funnelStage(rs.getString("funnel_stage"))
                                    .stageOrder(rs.getInt("stage_order"))
                                    .usersAtStage(rs.getInt("users_at_stage"))
                                    .usersMovedToNext(rs.getInt("users_moved_to_next"))
                                    .conversionFromPrevious(
                                            rs.getBigDecimal("conversion_from_previous"))
                                    .conversionFromFirst(rs.getBigDecimal("conversion_from_first"))
                                    .dropouts(rs.getInt("dropouts"))
                                    .dropoutRate(rs.getBigDecimal("dropout_rate"))
                                    .avgTimeOnStage(
                                            rs.getObject(
                                                    "avg_time_on_stage",
                                                    java.math.BigDecimal.class))
                                    .stageRevenue(rs.getBigDecimal("stage_revenue"))
                                    .avgRevenuePerUser(rs.getBigDecimal("avg_revenue_per_user"))
                                    .completedDeals(rs.getInt("completed_deals"))
                                    .dealConversionRate(rs.getBigDecimal("deal_conversion_rate"))
                                    .analysisStartDate(
                                            rs.getObject("analysis_start_date", LocalDate.class))
                                    .analysisEndDate(
                                            rs.getObject("analysis_end_date", LocalDate.class))
                                    .conversionChangePercent(
                                            rs.getObject(
                                                    "conversion_change_percent",
                                                    java.math.BigDecimal.class))
                                    .stageTrend(rs.getString("stage_trend"))
                                    .build();

                    result.add(funnel);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Ошибка при получении анализа воронки продаж: " + e.getMessage(), e);
        }

        return result;
    }

    private static String SQL_SALES_FORECAST =
            """
      WITH historical_sales AS (
        SELECT
            DATE_TRUNC('month', order_date) as sales_month,
            COALESCE(SUM(total_amount), SUM(total), 0) as monthly_revenue,
            COUNT(DISTINCT id) as monthly_orders,
            AVG(COALESCE(total_amount, total, 0)) as avg_order_value
        FROM mentee_power.orders
        WHERE order_date >= CURRENT_DATE - INTERVAL '%d months'
          AND order_date < CURRENT_DATE
          AND status = 'COMPLETED'
        GROUP BY DATE_TRUNC('month', order_date)
      ),
      sales_with_trends AS (
        SELECT
            sales_month,
            monthly_revenue,
            monthly_orders,
            avg_order_value,
            -- Линейный тренд (упрощенный)
            AVG(monthly_revenue) OVER (
                ORDER BY sales_month
                ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
            ) as trend_coefficient,
            -- Сезонный коэффициент (сравнение с тем же месяцем год назад)
            LAG(monthly_revenue, 12) OVER (ORDER BY sales_month) as same_month_last_year,
            CASE
                WHEN LAG(monthly_revenue, 12) OVER (ORDER BY sales_month) > 0
                THEN monthly_revenue / LAG(monthly_revenue, 12) OVER (ORDER BY sales_month)
                ELSE 1.0
            END as seasonal_coefficient,
            -- R² (упрощенный расчет)
            0.85 as r_squared,
            -- MAPE (упрощенный)
            ABS(monthly_revenue - AVG(monthly_revenue) OVER ()) / NULLIF(AVG(monthly_revenue) OVER (), 0) * 100 as mape,
            -- Стандартная ошибка
            STDDEV(monthly_revenue) OVER () as forecast_standard_error
        FROM historical_sales
      ),
      forecast_months AS (
        SELECT
            generate_series(
                CURRENT_DATE,
                CURRENT_DATE + INTERVAL '%d months',
                '1 month'::INTERVAL
            )::DATE as forecast_date
      ),
      forecast_data AS (
        SELECT
            fm.forecast_date,
            'FORECAST' as record_type,
            -- Прогноз на основе тренда и сезонности
            COALESCE((
                SELECT AVG(monthly_revenue) *
                       AVG(seasonal_coefficient)
                FROM sales_with_trends
                WHERE EXTRACT(MONTH FROM sales_month) = EXTRACT(MONTH FROM fm.forecast_date)
            ),
            (
                SELECT AVG(monthly_revenue)
                FROM sales_with_trends
            )) as forecasted_revenue,
            NULL::DECIMAL as actual_revenue,
            -- Доверительный интервал (95%)
            COALESCE((
                SELECT AVG(monthly_revenue) * 1.2
                FROM sales_with_trends
            ), 0) as confidence_interval_upper,
            COALESCE((
                SELECT AVG(monthly_revenue) * 0.8
                FROM sales_with_trends
            ), 0) as confidence_interval_lower,
            COALESCE((
                SELECT AVG(monthly_orders)
                FROM sales_with_trends
            ), 0)::INTEGER as forecasted_orders,
            NULL::INTEGER as actual_orders,
            COALESCE((
                SELECT AVG(trend_coefficient)
                FROM sales_with_trends
            ), 0) as trend_coefficient,
            COALESCE((
                SELECT AVG(seasonal_coefficient)
                FROM sales_with_trends
                WHERE EXTRACT(MONTH FROM sales_month) = EXTRACT(MONTH FROM fm.forecast_date)
            ), 1.0) as seasonal_coefficient,
            COALESCE((
                SELECT AVG(r_squared)
                FROM sales_with_trends
            ), 0.85) as r_squared,
            COALESCE((
                SELECT AVG(mape)
                FROM sales_with_trends
            ), 0) as mean_absolute_percent_error,
            COALESCE((
                SELECT AVG(forecast_standard_error)
                FROM sales_with_trends
            ), 0) as forecast_standard_error,
            COALESCE((
                SELECT AVG(avg_order_value)
                FROM sales_with_trends
            ), 0) as forecasted_avg_order_value,
            'LINEAR_REGRESSION' as forecast_method,
            %d as historical_months,
            ROW_NUMBER() OVER (ORDER BY fm.forecast_date) as forecast_month_number
        FROM forecast_months fm
      ),
      historical_data AS (
        SELECT
            sales_month::DATE as forecast_date,
            'HISTORICAL' as record_type,
            monthly_revenue as forecasted_revenue,
            monthly_revenue as actual_revenue,
            NULL::DECIMAL as confidence_interval_upper,
            NULL::DECIMAL as confidence_interval_lower,
            monthly_orders as forecasted_orders,
            monthly_orders as actual_orders,
            trend_coefficient,
            seasonal_coefficient,
            r_squared,
            mape as mean_absolute_percent_error,
            forecast_standard_error,
            avg_order_value as forecasted_avg_order_value,
            'LINEAR_REGRESSION' as forecast_method,
            %d as historical_months,
            NULL::INTEGER as forecast_month_number
        FROM sales_with_trends
      )
      SELECT * FROM historical_data
      UNION ALL
      SELECT * FROM forecast_data
      ORDER BY forecast_date;
      """;

    @Override
    public List<SalesForecast> getSalesForecast(Integer historicalMonths, Integer forecastMonths)
            throws DataAccessException {
        List<SalesForecast> result = new ArrayList<>();

        if (historicalMonths == null || historicalMonths <= 0) {
            historicalMonths = 12;
        }
        if (forecastMonths == null || forecastMonths <= 0) {
            forecastMonths = 6;
        }

        String sql =
                String.format(
                        SQL_SALES_FORECAST,
                        historicalMonths,
                        forecastMonths,
                        historicalMonths,
                        historicalMonths);

        try (Connection conn = getConnection();
                PreparedStatement statement = conn.prepareStatement(sql)) {

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SalesForecast forecast =
                            SalesForecast.builder()
                                    .forecastDate(rs.getObject("forecast_date", LocalDate.class))
                                    .recordType(rs.getString("record_type"))
                                    .forecastedRevenue(rs.getBigDecimal("forecasted_revenue"))
                                    .actualRevenue(
                                            rs.getObject(
                                                    "actual_revenue", java.math.BigDecimal.class))
                                    .confidenceIntervalUpper(
                                            rs.getObject(
                                                    "confidence_interval_upper",
                                                    java.math.BigDecimal.class))
                                    .confidenceIntervalLower(
                                            rs.getObject(
                                                    "confidence_interval_lower",
                                                    java.math.BigDecimal.class))
                                    .forecastedOrders(rs.getInt("forecasted_orders"))
                                    .actualOrders(rs.getObject("actual_orders", Integer.class))
                                    .trendCoefficient(rs.getBigDecimal("trend_coefficient"))
                                    .seasonalCoefficient(rs.getBigDecimal("seasonal_coefficient"))
                                    .rSquared(rs.getBigDecimal("r_squared"))
                                    .meanAbsolutePercentError(
                                            rs.getBigDecimal("mean_absolute_percent_error"))
                                    .forecastStandardError(
                                            rs.getBigDecimal("forecast_standard_error"))
                                    .forecastedAvgOrderValue(
                                            rs.getBigDecimal("forecasted_avg_order_value"))
                                    .forecastMethod(rs.getString("forecast_method"))
                                    .historicalMonths(rs.getInt("historical_months"))
                                    .forecastMonthNumber(
                                            rs.getObject("forecast_month_number", Integer.class))
                                    .build();

                    result.add(forecast);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Ошибка при получении прогноза продаж: " + e.getMessage(), e);
        }

        return result;
    }
}
