--liquibase formatted sql
--changeset mp168:create-test-data-mp168

-- Тестовые данные для таблиц

TRUNCATE mentee_power.promotion_usage, mentee_power.reviews, mentee_power.promotions, mentee_power.categories, mentee_power.accounts, mentee_power.transfers, mentee_power.orders, mentee_power.order_items, mentee_power.products, mentee_power.inventory_locks, mentee_power.users RESTART IDENTITY CASCADE;

-- Пользователи для тестирования
INSERT INTO mentee_power.users (email, first_name, last_name, last_login, is_active, name) VALUES
    ('alice@example.com', 'Alice', 'Johnson', NOW() - interval '1 hour', true, 'Alice Johnson'),
    ('bob@example.com', 'Bob', 'Smith', NOW() - interval '2 hours', true, 'Bob Smith'),
    ('charlie@example.com', 'Charlie', 'Brown', NOW() - interval '30 minutes', true, 'Charlie Brown'),
    ('diana@example.com', 'Diana', 'Wilson', NOW() - interval '15 minutes', true, 'Diana Wilson');

-- Счета с балансами для трансферов
INSERT INTO mentee_power.accounts (user_id, balance, account_type, last_updated, status, updated_at) VALUES
    (1, 5000.00, 'CHECKING', NOW(), 'ACTIVE', NOW()),
    (2, 3000.00, 'CHECKING', NOW(), 'ACTIVE', NOW()),
    (3, 8000.00, 'SAVINGS', NOW(), 'ACTIVE', NOW()),
    (4, 2500.00, 'CHECKING', NOW(), 'ACTIVE', NOW());

-- Товары с ограниченным количеством для inventory conflicts
INSERT INTO mentee_power.products (name, sku, stock_quantity, price, last_stock_update) VALUES
    ('Limited GPU RTX 4090', 'GPU-RTX4090-001', 5, 1599.99, NOW()),
    ('Gaming Console PS5', 'CONSOLE-PS5-001', 3, 499.99, NOW()),
    ('Rare Collectible', 'COLLECTIBLE-001', 1, 999.99, NOW()),
    ('Premium Laptop', 'LAPTOP-PREMIUM-001', 2, 2999.99, NOW()),
    ('Smartphone iPhone 15', 'PHONE-IP15-001', 4, 1199.99, NOW());

-- Заказы для тестирования FK дедлоков
INSERT INTO mentee_power.orders (user_id, total_amount, status, created_at, total) VALUES
    (1, 1599.99, 'PENDING', NOW() - interval '1 hour', 1599.99),
    (2, 499.99, 'CONFIRMED', NOW() - interval '30 minutes', 499.99),
    (3, 999.99, 'PROCESSING', NOW() - interval '15 minutes', 999.99),
    (4, 2999.99, 'PENDING', NOW() - interval '5 minutes', 2999.99);

-- Позиции заказов
INSERT INTO mentee_power.order_items (order_id, product_id, quantity, unit_price, status, price) VALUES
    (1, 1, 1, 1599.99, 'RESERVED', 1599.99),
    (2, 2, 1, 499.99, 'CONFIRMED', 499.99),
    (3, 3, 1, 999.99, 'PROCESSING', 999.99),
    (4, 4, 1, 2999.99, 'PENDING', 2999.99);

-- Категории
INSERT INTO mentee_power.categories (id, name, category_type, parent_id) VALUES
    (1, 'Electronics', 'ELECTRONICS', NULL),
    (2, 'Clothing', 'CLOTHING', NULL),
    (3, 'Books', 'BOOKS', NULL),
    (4, 'Home & Garden', 'HOME_GARDEN', NULL),
    (5, 'Laptops', 'ELECTRONICS', 1),
    (6, 'Smartphones', 'ELECTRONICS', 1),
    (7, 'Men''s Clothing', 'CLOTHING', 2),
    (8, 'Women''s Clothing', 'CLOTHING', 2),
    (9, 'Fiction', 'BOOKS', 3),
    (10, 'Non-Fiction', 'BOOKS', 3)
ON CONFLICT (id) DO NOTHING;

-- Обновляем существующих пользователей с расширенными атрибутами
UPDATE mentee_power.users SET 
    customer_tier = CASE 
        WHEN id % 10 = 0 THEN 'PLATINUM'
        WHEN id % 5 = 0 THEN 'GOLD' 
        WHEN id % 3 = 0 THEN 'SILVER'
        ELSE 'BRONZE'
    END,
    lifetime_value = (random() * 50000)::decimal(10,2),
    last_purchase_date = NOW() - (random() * interval '365 days')
WHERE customer_tier IS NULL OR lifetime_value IS NULL;

-- Добавляем больше пользователей для тестирования
INSERT INTO mentee_power.users (email, first_name, last_name, name, customer_tier, lifetime_value, last_purchase_date, registration_date, is_active)
SELECT 
    'user' || generate_series || '@test.com',
    'User' || generate_series,
    'LastName' || generate_series,
    'User' || generate_series || ' LastName' || generate_series,
    CASE 
        WHEN generate_series % 10 = 0 THEN 'PLATINUM'
        WHEN generate_series % 5 = 0 THEN 'GOLD' 
        WHEN generate_series % 3 = 0 THEN 'SILVER'
        ELSE 'BRONZE'
    END,
    (random() * 50000)::decimal(10,2),
    NOW() - (random() * interval '365 days'),
    NOW() - (random() * interval '2 years'),
    true
FROM generate_series(5, 1000)
ON CONFLICT (email) DO NOTHING;

-- Добавляем больше заказов
INSERT INTO mentee_power.orders (user_id, total_amount, total, status, created_at, region)
SELECT 
    (SELECT id FROM mentee_power.users ORDER BY random() LIMIT 1),
    (random() * 5000 + 100)::decimal(10,2),
    (random() * 5000 + 100)::decimal(10,2),
    CASE (random() * 4)::int
        WHEN 0 THEN 'PENDING'
        WHEN 1 THEN 'CONFIRMED'
        WHEN 2 THEN 'PROCESSING'
        WHEN 3 THEN 'DELIVERED'
        ELSE 'COMPLETED'
    END,
    NOW() - (random() * interval '2 years'),
    CASE (random() * 3)::int
        WHEN 0 THEN 'MOSCOW'
        WHEN 1 THEN 'SPB'
        ELSE 'EKATERINBURG'
    END
FROM generate_series(5, 50000)
ON CONFLICT DO NOTHING;

-- Добавляем больше товаров
INSERT INTO mentee_power.products (name, sku, stock_quantity, price, category_id, created_at)
SELECT 
    'Product ' || generate_series,
    'SKU-' || generate_series,
    (random() * 100)::int,
    (random() * 1000 + 10)::decimal(10,2),
    (random() * 10 + 1)::bigint,
    NOW() - (random() * interval '1 year')
FROM generate_series(6, 10000)
ON CONFLICT (sku) DO NOTHING;

-- Добавляем отзывы
INSERT INTO mentee_power.reviews (user_id, product_id, rating, review_text, created_at, is_verified, helpful_votes)
SELECT 
    (SELECT id FROM mentee_power.users ORDER BY random() LIMIT 1),
    (SELECT id FROM mentee_power.products ORDER BY random() LIMIT 1),
    (random() * 4 + 1)::int,
    'Review text ' || generate_series || ' - this product is amazing and I highly recommend it to everyone',
    NOW() - (random() * interval '2 years'),
    random() > 0.3,
    (random() * 50)::int
FROM generate_series(1, 20000)
ON CONFLICT DO NOTHING;

-- Добавляем промоакции
INSERT INTO mentee_power.promotions (name, promo_code, promotion_type, discount_value, start_date, end_date, usage_limit, is_active)
VALUES
('Black Friday 2024', 'BLACKFRIDAY24', 'PERCENTAGE', 25.0, '2024-11-24', '2024-11-30', 10000, true),
('New Year Sale', 'NEWYEAR25', 'PERCENTAGE', 15.0, '2024-12-31', '2025-01-07', 5000, true),
('Summer Discount', 'SUMMER20', 'FIXED_AMOUNT', 50.0, '2024-06-01', '2024-08-31', 20000, false),
('Welcome Bonus', 'WELCOME10', 'PERCENTAGE', 10.0, '2024-01-01', '2024-12-31', 50000, true),
('VIP Exclusive', 'VIP30', 'PERCENTAGE', 30.0, '2024-01-01', '2024-12-31', 1000, true)
ON CONFLICT (promo_code) DO NOTHING;

-- Добавляем использование промокодов
INSERT INTO mentee_power.promotion_usage (user_id, promotion_id, order_id, discount_applied, used_at)
SELECT 
    (SELECT id FROM mentee_power.users ORDER BY random() LIMIT 1),
    (SELECT id FROM mentee_power.promotions ORDER BY random() LIMIT 1),
    (SELECT id FROM mentee_power.orders ORDER BY random() LIMIT 1),
    (random() * 200 + 10)::decimal(10,2),
    NOW() - (random() * interval '1 year')
FROM generate_series(1, 5000)
ON CONFLICT DO NOTHING;

-- Обновляем категории с иерархией
UPDATE mentee_power.categories SET 
    category_type = CASE (id % 4)
        WHEN 0 THEN 'ELECTRONICS'
        WHEN 1 THEN 'CLOTHING'
        WHEN 2 THEN 'BOOKS'
        ELSE 'HOME_GARDEN'
    END,
    parent_id = CASE 
        WHEN id > 10 THEN (id % 10 + 1)
        ELSE NULL
    END
WHERE category_type IS NULL OR parent_id IS NULL;

ANALYZE mentee_power.users, mentee_power.orders, mentee_power.order_items, mentee_power.products, mentee_power.categories, mentee_power.reviews, mentee_power.promotions, mentee_power.promotion_usage;

--rollback TRUNCATE mentee_power.promotion_usage, mentee_power.reviews, mentee_power.promotions, mentee_power.categories, mentee_power.accounts, mentee_power.transfers, mentee_power.orders, mentee_power.order_items, mentee_power.products, mentee_power.inventory_locks, mentee_power.users RESTART IDENTITY CASCADE;

