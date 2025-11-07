/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.repository.postgres;

/**
 * Builder для построения CTE (Common Table Expression) запросов.
 * Предоставляет методы для создания различных типов CTE запросов для аналитики.
 */
public class CteQueryBuilder {

    /**
     * Строит CTE запрос для сегментации клиентов.
     * Использует множественные CTE для декомпозиции логики.
     *
     * @return SQL запрос с CTE для сегментации клиентов
     */
    public static String buildSegmentationCte() {
        return """
            WITH customer_metrics AS (
                SELECT
                    u.id,
                    u.name,
                    u.email,
                    u.registration_date,
                    COUNT(DISTINCT o.id) as total_orders,
                    COALESCE(SUM(o.total_amount), 0) as total_spent,
                    COALESCE(AVG(o.total_amount), 0) as avg_order_value,
                    MAX(o.order_date) as last_order_date,
                    MIN(o.order_date) as first_order_date
                FROM mentee_power.users u
                LEFT JOIN mentee_power.orders o ON u.id = o.user_id
                WHERE (o.status = 'COMPLETED' OR o.status = 'DELIVERED' OR o.status IS NULL)
                GROUP BY u.id, u.name, u.email, u.registration_date
            ),
            customer_segments AS (
                SELECT
                    *,
                    CASE
                        WHEN total_orders >= 10 AND total_spent >= 50000 THEN 'VIP'
                        WHEN total_orders >= 5 AND total_spent >= 20000 THEN 'PREMIUM'
                        WHEN total_orders >= 2 AND total_spent >= 5000 THEN 'REGULAR'
                        WHEN total_orders >= 1 THEN 'NEW'
                        ELSE 'INACTIVE'
                    END as segment,
                    CASE
                        WHEN last_order_date >= CURRENT_DATE - INTERVAL '30 days' THEN 'ACTIVE'
                        WHEN last_order_date >= CURRENT_DATE - INTERVAL '90 days' THEN 'AT_RISK'
                        ELSE 'CHURNED'
                    END as activity_status
                FROM customer_metrics
            ),
            segment_summary AS (
                SELECT
                    segment,
                    activity_status,
                    COUNT(*) as customers_count,
                    AVG(total_spent) as avg_total_spent,
                    AVG(avg_order_value) as avg_order_value,
                    SUM(total_spent) as segment_revenue
                FROM customer_segments
                GROUP BY segment, activity_status
            )
            SELECT
                segment,
                activity_status,
                customers_count,
                ROUND(avg_total_spent, 2) as avg_total_spent,
                ROUND(avg_order_value, 2) as avg_order_value,
                segment_revenue,
                ROUND(
                    (segment_revenue / SUM(segment_revenue) OVER () * 100),
                    2
                ) as revenue_share_percent
            FROM segment_summary
            ORDER BY segment_revenue DESC;
            """;
    }

    /**
     * Строит рекурсивный CTE запрос для иерархии категорий.
     * Использует рекурсивный CTE для обхода дерева категорий.
     *
     * @return SQL запрос с рекурсивным CTE для иерархии категорий
     */
    public static String buildRecursiveHierarchy() {
        return """
            WITH RECURSIVE category_hierarchy AS (
                -- Якорный запрос: корневые категории
                SELECT
                    id,
                    name,
                    parent_id,
                    0 as level,
                    CAST(name AS VARCHAR) as full_path,
                    ARRAY[id] as path_ids
                FROM mentee_power.categories
                WHERE parent_id IS NULL
                  AND is_active = true

                UNION ALL

                -- Рекурсивная часть: дочерние категории
                SELECT
                    c.id,
                    c.name,
                    c.parent_id,
                    ch.level + 1,
                    CAST(ch.full_path || ' > ' || c.name AS VARCHAR) as full_path,
                    ch.path_ids || c.id
                FROM mentee_power.categories c
                INNER JOIN category_hierarchy ch ON c.parent_id = ch.id
                WHERE c.is_active = true
                  AND NOT (c.id = ANY(ch.path_ids))  -- Предотвращение циклов
                  AND ch.level < 10  -- Ограничение глубины
            ),
            category_stats AS (
                SELECT
                    ch.id,
                    ch.name,
                    ch.level,
                    ch.full_path,
                    LPAD('', ch.level * 2, ' ') || ch.name as indented_name,
                    COUNT(DISTINCT p.id) as products_count,
                    COUNT(DISTINCT oi.order_id) as orders_count,
                    COALESCE(SUM(oi.price * oi.quantity), 0) as total_revenue,
                    (SELECT ch2.id FROM category_hierarchy ch2
                     WHERE ch2.path_ids[1] = ch.path_ids[1]
                     LIMIT 1) as root_category_id
                FROM category_hierarchy ch
                LEFT JOIN mentee_power.products p ON ch.id = p.category_id
                LEFT JOIN mentee_power.order_items oi ON p.id = oi.product_id
                LEFT JOIN mentee_power.orders o ON oi.order_id = o.id
                WHERE (o.status = 'COMPLETED' OR o.status = 'DELIVERED' OR o.status IS NULL)
                GROUP BY ch.id, ch.name, ch.level, ch.full_path, ch.path_ids
            ),
            root_revenues AS (
                SELECT
                    cs.root_category_id,
                    cs.total_revenue as root_category_revenue
                FROM category_stats cs
                WHERE cs.level = 0
            )
            SELECT
                cs.indented_name,
                cs.full_path,
                cs.level,
                cs.products_count,
                cs.orders_count,
                cs.total_revenue,
                COALESCE(rr.root_category_revenue, cs.total_revenue) as root_category_revenue
            FROM category_stats cs
            LEFT JOIN root_revenues rr ON cs.root_category_id = rr.root_category_id
            ORDER BY cs.level, cs.name;
            """;
    }

    /**
     * Строит CTE запрос для когортного анализа.
     * Анализирует удержание клиентов по месяцам.
     *
     * @return SQL запрос с CTE для когортного анализа
     */
    public static String buildCohortAnalysis() {
        return """
            WITH first_purchase AS (
                SELECT
                    o.user_id,
                    DATE_TRUNC('month', MIN(COALESCE(o.order_date, o.created_at::DATE))) as cohort_month
                FROM mentee_power.orders o
                WHERE (o.status = 'COMPLETED' OR o.status = 'DELIVERED')
                GROUP BY o.user_id
            ),
            monthly_activity AS (
                SELECT
                    fp.cohort_month,
                    DATE_TRUNC('month', COALESCE(o.order_date, o.created_at::DATE)) as activity_month,
                    o.user_id,
                    COUNT(DISTINCT o.id) as orders_count,
                    SUM(o.total_amount) as monthly_spent
                FROM first_purchase fp
                JOIN mentee_power.orders o ON fp.user_id = o.user_id
                WHERE (o.status = 'COMPLETED' OR o.status = 'DELIVERED')
                GROUP BY fp.cohort_month, DATE_TRUNC('month', COALESCE(o.order_date, o.created_at::DATE)), o.user_id
            ),
            cohort_summary AS (
                SELECT
                    fp.cohort_month,
                    COUNT(DISTINCT fp.user_id) as cohort_size,
                    ma.activity_month,
                    EXTRACT(MONTH FROM AGE(ma.activity_month, fp.cohort_month))::INTEGER as month_number,
                    COUNT(DISTINCT ma.user_id) as active_customers,
                    AVG(ma.monthly_spent / NULLIF(ma.orders_count, 0)) as avg_order_value,
                    SUM(ma.monthly_spent) as cohort_revenue
                FROM first_purchase fp
                LEFT JOIN monthly_activity ma ON fp.user_id = ma.user_id
                GROUP BY fp.cohort_month, ma.activity_month
            )
            SELECT
                cohort_month,
                cohort_size,
                month_number,
                active_customers,
                CASE
                    WHEN month_number = 0 THEN 100.0
                    ELSE ROUND((active_customers::DECIMAL / NULLIF(cohort_size, 0) * 100), 2)
                END as retention_rate,
                ROUND(COALESCE(avg_order_value, 0), 2) as avg_order_value,
                COALESCE(cohort_revenue, 0) as cohort_revenue
            FROM cohort_summary
            WHERE month_number IS NOT NULL
            ORDER BY cohort_month, month_number;
            """;
    }

    /**
     * Строит CTE запрос для ABC анализа продуктов.
     * Классифицирует продукты по выручке на категории A, B, C.
     *
     * @return SQL запрос с CTE для ABC анализа
     */
    public static String buildAbcAnalysis() {
        return """
            WITH product_sales AS (
                SELECT
                    p.id as product_id,
                    p.name as product_name,
                    c.name as category_name,
                    COALESCE(SUM(oi.price * oi.quantity), 0) as total_revenue
                FROM mentee_power.products p
                LEFT JOIN mentee_power.categories c ON p.category_id = c.id
                LEFT JOIN mentee_power.order_items oi ON p.id = oi.product_id
                LEFT JOIN mentee_power.orders o ON oi.order_id = o.id
                WHERE (o.status = 'COMPLETED' OR o.status = 'DELIVERED' OR o.status IS NULL)
                GROUP BY p.id, p.name, c.name
            ),
            ranked_products AS (
                SELECT
                    *,
                    RANK() OVER (ORDER BY total_revenue DESC) as revenue_rank,
                    SUM(total_revenue) OVER (ORDER BY total_revenue DESC) as cumulative_revenue,
                    SUM(total_revenue) OVER () as total_market_revenue
                FROM product_sales
            ),
            abc_categorized AS (
                SELECT
                    product_id,
                    product_name,
                    category_name,
                    total_revenue,
                    cumulative_revenue,
                    ROUND(
                        (cumulative_revenue / NULLIF(total_market_revenue, 0) * 100),
                        2
                    ) as cumulative_percent,
                    CASE
                        WHEN (cumulative_revenue / NULLIF(total_market_revenue, 0) * 100) <= 80 THEN 'A'
                        WHEN (cumulative_revenue / NULLIF(total_market_revenue, 0) * 100) <= 95 THEN 'B'
                        ELSE 'C'
                    END as abc_category,
                    revenue_rank
                FROM ranked_products
            )
            SELECT
                product_id,
                product_name,
                category_name,
                total_revenue,
                cumulative_revenue,
                cumulative_percent,
                abc_category,
                revenue_rank
            FROM abc_categorized
            ORDER BY revenue_rank;
            """;
    }

    /**
     * Строит CTE запрос для анализа трендов продуктов.
     * Анализирует динамику продаж продуктов по месяцам с расчетом роста.
     *
     * @return SQL запрос с CTE для анализа трендов продуктов
     */
    public static String buildProductTrends() {
        return """
            WITH monthly_product_sales AS (
                SELECT
                    p.id as product_id,
                    p.name as product_name,
                    c.name as category_name,
                    DATE_TRUNC('month', COALESCE(o.order_date, o.created_at::DATE)) as sales_month,
                    SUM(oi.price * oi.quantity) as revenue,
                    SUM(oi.quantity) as units_sold
                FROM mentee_power.products p
                JOIN mentee_power.categories c ON p.category_id = c.id
                JOIN mentee_power.order_items oi ON p.id = oi.product_id
                JOIN mentee_power.orders o ON oi.order_id = o.id
                WHERE (o.status = 'COMPLETED' OR o.status = 'DELIVERED')
                  AND (COALESCE(o.order_date, o.created_at::DATE) >= CURRENT_DATE - INTERVAL '12 months')
                GROUP BY p.id, p.name, c.name, DATE_TRUNC('month', COALESCE(o.order_date, o.created_at::DATE))
            ),
            product_trends AS (
                SELECT
                    *,
                    LAG(revenue) OVER (PARTITION BY product_id ORDER BY sales_month) as prev_revenue,
                    LAG(units_sold) OVER (PARTITION BY product_id ORDER BY sales_month) as prev_units_sold
                FROM monthly_product_sales
            ),
            growth_analysis AS (
                SELECT
                    product_name,
                    category_name,
                    sales_month,
                    revenue,
                    units_sold,
                    CASE
                        WHEN prev_revenue IS NULL OR prev_revenue = 0 THEN NULL
                        ELSE ROUND(((revenue - prev_revenue) / prev_revenue * 100), 2)
                    END as revenue_growth_percent,
                    CASE
                        WHEN prev_units_sold IS NULL OR prev_units_sold = 0 THEN NULL
                        ELSE ROUND(((units_sold - prev_units_sold) / prev_units_sold * 100), 2)
                    END as units_growth_percent
                FROM product_trends
            )
            SELECT
                product_name,
                category_name,
                sales_month,
                revenue,
                units_sold,
                revenue_growth_percent,
                units_growth_percent,
                CASE
                    WHEN revenue_growth_percent > 20 THEN 'GROWING_FAST'
                    WHEN revenue_growth_percent > 5 THEN 'GROWING'
                    WHEN revenue_growth_percent IS NULL THEN 'NEW'
                    WHEN revenue_growth_percent > -5 THEN 'STABLE'
                    ELSE 'DECLINING'
                END as trend_category
            FROM growth_analysis
            WHERE sales_month IS NOT NULL
            ORDER BY sales_month DESC, revenue DESC;
            """;
    }

    /**
     * Оптимизирует сложный CTE запрос.
     * Добавляет подсказки и оптимизации для производительности.
     *
     * @param baseQuery базовый CTE запрос
     * @return оптимизированный запрос
     */
    public static String optimizeComplexCte(String baseQuery) {
        // В реальном проекте здесь можно добавить оптимизации:
        // - Добавление LIMIT для ограничения результатов
        // - Добавление индексов через комментарии
        // - Оптимизация JOIN'ов
        return baseQuery;
    }
}
