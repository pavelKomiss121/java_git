--liquibase formatted sql
--changeset mp170:create-window-functions-test-data

-- Тестовые данные для оконных функций (MP-170)
-- Регионы
INSERT INTO mentee_power.regions (name, country, manager_name, is_active)
VALUES
    ('Москва', 'Россия', 'Иван Петров', true),
    ('Санкт-Петербург', 'Россия', 'Мария Сидорова', true),
    ('Екатеринбург', 'Россия', 'Алексей Иванов', true),
    ('Новосибирск', 'Россия', 'Ольга Смирнова', true),
    ('Казань', 'Россия', 'Дмитрий Козлов', true)
ON CONFLICT DO NOTHING;

-- Продукты для продаж
INSERT INTO mentee_power.products_sales_analytics (name, category, price, cost, is_active)
SELECT
    'Продукт ' || generate_series,
    CASE (generate_series % 4)
        WHEN 0 THEN 'ELECTRONICS'
        WHEN 1 THEN 'CLOTHING'
        WHEN 2 THEN 'BOOKS'
        ELSE 'HOME_GARDEN'
    END,
    (random() * 50000 + 1000)::decimal(10,2),
    (random() * 30000 + 500)::decimal(10,2),
    true
FROM generate_series(1, 50)
ON CONFLICT DO NOTHING;

-- Продавцы
INSERT INTO mentee_power.sales_people (name, email, region_id, hire_date, base_salary, commission_rate, status)
SELECT
    'Продавец ' || generate_series,
    'salesperson' || generate_series || '@example.com',
    (SELECT id FROM mentee_power.regions ORDER BY random() LIMIT 1),
    CURRENT_DATE - (random() * 1825)::int, -- последние 5 лет
    (random() * 50000 + 30000)::decimal(10,2),
    (random() * 0.05 + 0.01)::decimal(5,4), -- от 1% до 6%
    CASE (random() * 10)::int
        WHEN 0 THEN 'INACTIVE'
        ELSE 'ACTIVE'
    END
FROM generate_series(1, 30)
ON CONFLICT DO NOTHING;

-- Транзакции продаж
INSERT INTO mentee_power.sales_transactions (salesperson_id, product_id, amount, quantity, sale_date, status, notes)
SELECT
    (SELECT id FROM mentee_power.sales_people WHERE status = 'ACTIVE' ORDER BY random() LIMIT 1),
    (SELECT id FROM mentee_power.products_sales_analytics ORDER BY random() LIMIT 1),
    (random() * 100000 + 1000)::decimal(10,2),
    (random() * 10 + 1)::int,
    CURRENT_DATE - (random() * 90)::int, -- последние 90 дней
    'COMPLETED',
    CASE (random() * 5)::int
        WHEN 0 THEN 'Крупная сделка'
        WHEN 1 THEN 'Повторный клиент'
        WHEN 2 THEN 'Специальное предложение'
        ELSE NULL
    END
FROM generate_series(1, 1000)
ON CONFLICT DO NOTHING;

-- Обновление статистики для оптимизатора запросов
ANALYZE mentee_power.regions, mentee_power.products_sales_analytics, mentee_power.sales_people, mentee_power.sales_transactions;

--rollback TRUNCATE mentee_power.sales_transactions, mentee_power.sales_people, mentee_power.products_sales_analytics, mentee_power.regions RESTART IDENTITY CASCADE;

