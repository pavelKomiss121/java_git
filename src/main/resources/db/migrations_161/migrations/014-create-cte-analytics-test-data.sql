--liquibase formatted sql
--changeset mp171:create-cte-analytics-test-data

-- Тестовые данные для иерархии категорий (для демонстрации рекурсивного CTE)
-- Обновляем существующие или вставляем новые категории

-- Обновляем или вставляем корневые категории
INSERT INTO mentee_power.categories (name, description, parent_id, is_active) VALUES
('Electronics', 'Electronic devices and accessories', NULL, true),
('Clothing', 'Apparel and fashion items', NULL, true),
('Home & Garden', 'Home improvement and garden supplies', NULL, true)
ON CONFLICT (id) DO UPDATE SET
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

-- Но так как нет уникального индекса на name, используем другой подход
-- Обновляем существующие корневые категории
UPDATE mentee_power.categories 
SET description = 'Electronic devices and accessories', is_active = true
WHERE name = 'Electronics' AND parent_id IS NULL;

UPDATE mentee_power.categories 
SET description = 'Apparel and fashion items', is_active = true
WHERE name = 'Clothing' AND parent_id IS NULL;

UPDATE mentee_power.categories 
SET description = 'Home improvement and garden supplies', is_active = true
WHERE name = 'Home & Garden' AND parent_id IS NULL;

-- Вставляем корневые категории, если их нет
INSERT INTO mentee_power.categories (name, description, parent_id, is_active)
SELECT 'Electronics', 'Electronic devices and accessories', NULL, true
WHERE NOT EXISTS (SELECT 1 FROM mentee_power.categories WHERE name = 'Electronics' AND parent_id IS NULL);

INSERT INTO mentee_power.categories (name, description, parent_id, is_active)
SELECT 'Clothing', 'Apparel and fashion items', NULL, true
WHERE NOT EXISTS (SELECT 1 FROM mentee_power.categories WHERE name = 'Clothing' AND parent_id IS NULL);

INSERT INTO mentee_power.categories (name, description, parent_id, is_active)
SELECT 'Home & Garden', 'Home improvement and garden supplies', NULL, true
WHERE NOT EXISTS (SELECT 1 FROM mentee_power.categories WHERE name = 'Home & Garden' AND parent_id IS NULL);

-- Обновляем или вставляем дочерние категории уровня 2
UPDATE mentee_power.categories 
SET description = 'Portable computers', is_active = true
WHERE name = 'Laptops' AND parent_id = (SELECT id FROM mentee_power.categories WHERE name = 'Electronics' AND parent_id IS NULL LIMIT 1);

UPDATE mentee_power.categories 
SET description = 'Mobile phones and accessories', is_active = true
WHERE name = 'Smartphones' AND parent_id = (SELECT id FROM mentee_power.categories WHERE name = 'Electronics' AND parent_id IS NULL LIMIT 1);

UPDATE mentee_power.categories 
SET description = 'Tablet computers', is_active = true
WHERE name = 'Tablets' AND parent_id = (SELECT id FROM mentee_power.categories WHERE name = 'Electronics' AND parent_id IS NULL LIMIT 1);

UPDATE mentee_power.categories 
SET description = 'Clothing for men', is_active = true
WHERE name = 'Men Clothing' AND parent_id = (SELECT id FROM mentee_power.categories WHERE name = 'Clothing' AND parent_id IS NULL LIMIT 1);

UPDATE mentee_power.categories 
SET description = 'Clothing for women', is_active = true
WHERE name = 'Women Clothing' AND parent_id = (SELECT id FROM mentee_power.categories WHERE name = 'Clothing' AND parent_id IS NULL LIMIT 1);

-- Вставляем дочерние категории уровня 2, если их нет
INSERT INTO mentee_power.categories (name, description, parent_id, is_active)
SELECT 
    v.name, 
    v.description, 
    (SELECT id FROM mentee_power.categories WHERE name = v.parent_name AND parent_id IS NULL LIMIT 1) as parent_id,
    v.is_active
FROM (VALUES
    ('Laptops', 'Portable computers', 'Electronics', true),
    ('Smartphones', 'Mobile phones and accessories', 'Electronics', true),
    ('Tablets', 'Tablet computers', 'Electronics', true),
    ('Men Clothing', 'Clothing for men', 'Clothing', true),
    ('Women Clothing', 'Clothing for women', 'Clothing', true)
) AS v(name, description, parent_name, is_active)
WHERE NOT EXISTS (
    SELECT 1 FROM mentee_power.categories 
    WHERE categories.name = v.name 
      AND categories.parent_id = (SELECT id FROM mentee_power.categories WHERE name = v.parent_name AND parent_id IS NULL LIMIT 1)
);

-- Обновляем или вставляем дочерние категории уровня 3
UPDATE mentee_power.categories 
SET description = 'High-performance gaming laptops', is_active = true
WHERE name = 'Gaming Laptops' AND parent_id = (SELECT id FROM mentee_power.categories WHERE name = 'Laptops' AND parent_id IS NOT NULL LIMIT 1);

UPDATE mentee_power.categories 
SET description = 'Professional laptops for business', is_active = true
WHERE name = 'Business Laptops' AND parent_id = (SELECT id FROM mentee_power.categories WHERE name = 'Laptops' AND parent_id IS NOT NULL LIMIT 1);

-- Вставляем дочерние категории уровня 3, если их нет
INSERT INTO mentee_power.categories (name, description, parent_id, is_active)
SELECT 
    v.name, 
    v.description, 
    (SELECT id FROM mentee_power.categories WHERE name = v.parent_name AND parent_id IS NOT NULL LIMIT 1) as parent_id,
    v.is_active
FROM (VALUES
    ('Gaming Laptops', 'High-performance gaming laptops', 'Laptops', true),
    ('Business Laptops', 'Professional laptops for business', 'Laptops', true)
) AS v(name, description, parent_name, is_active)
WHERE NOT EXISTS (
    SELECT 1 FROM mentee_power.categories 
    WHERE categories.name = v.name 
      AND categories.parent_id = (SELECT id FROM mentee_power.categories WHERE name = v.parent_name AND parent_id IS NOT NULL LIMIT 1)
);

-- Тестовые пользователи для CTE аналитики
INSERT INTO mentee_power.users (name, email, registration_date) VALUES
('VIP Customer', 'vip@test.com', NOW() - INTERVAL '2 years'),
('Premium Customer', 'premium@test.com', NOW() - INTERVAL '1 year'),
('Regular Customer', 'regular@test.com', NOW() - INTERVAL '6 months'),
('New Customer', 'new@test.com', NOW() - INTERVAL '1 month'),
('Inactive Customer', 'inactive@test.com', NOW() - INTERVAL '1 year')
ON CONFLICT (email) DO UPDATE SET
    name = EXCLUDED.name,
    registration_date = EXCLUDED.registration_date;

-- Тестовые продукты для ABC анализа
INSERT INTO mentee_power.products (name, price, category_id, created_at)
SELECT 
    v.name,
    v.price,
    (SELECT id FROM mentee_power.categories WHERE name = v.category_name LIMIT 1),
    NOW() - (v.months_ago || ' months')::INTERVAL
FROM (VALUES
    ('High Revenue Product A', 10000.00, 'Electronics', 6),
    ('Medium Revenue Product B', 5000.00, 'Laptops', 4),
    ('Low Revenue Product C', 1000.00, 'Smartphones', 2)
) AS v(name, price, category_name, months_ago)
WHERE NOT EXISTS (SELECT 1 FROM mentee_power.products WHERE products.name = v.name)
ON CONFLICT DO NOTHING;

-- Тестовые заказы для ABC анализа
-- Product A: 10 заказов по 10000 + 5 заказов по 10000 = 150000 (75% от общего = 200000)
-- Product B: 3 заказа от Regular + 3 заказа от Premium = 6 заказов по 5000 = 30000 (cumulative A+B = 180000 = 90%)
-- Product C: 5 заказов по 2000 + 1 заказ от NEW = 12000 (cumulative = 192000 = 96%)
-- Нужно скорректировать, чтобы Product A был 80%, Product B cumulative был 95%

-- Заказы для Product A (VIP клиент) - 11 заказов по 10000 = 110000
INSERT INTO mentee_power.orders (user_id, total_amount, total, status, order_date, created_at)
SELECT 
    u.id,
    10000.00,
    10000.00,
    'COMPLETED',
    NOW() - (30 + generate_series * 10 || ' days')::INTERVAL,
    NOW() - (30 + generate_series * 10 || ' days')::INTERVAL
FROM mentee_power.users u
CROSS JOIN generate_series(0, 10)
WHERE u.email = 'vip@test.com';

-- Заказы для Product A (Premium клиент) - 5 заказов по 10000 = 50000 (PREMIUM: >= 5 заказов, >= 20000)
-- Итого Product A: 11 + 5 = 16 заказов по 10000 = 160000 (80% от 200000)
INSERT INTO mentee_power.orders (user_id, total_amount, total, status, order_date, created_at)
SELECT 
    u.id,
    10000.00,
    10000.00,
    'COMPLETED',
    NOW() - (60 + generate_series * 10 || ' days')::INTERVAL,
    NOW() - (60 + generate_series * 10 || ' days')::INTERVAL
FROM mentee_power.users u
CROSS JOIN generate_series(0, 4)
WHERE u.email = 'premium@test.com';

-- Дополнительные заказы для Product B от Premium клиента - 3 заказа по 5000 = 15000
-- Это нужно для ABC анализа, чтобы cumulative A+B был 95%
-- Итого Product B: 3 от Regular + 3 от Premium = 6 заказов по 5000 = 30000 (cumulative A+B = 190000 = 95%)
INSERT INTO mentee_power.orders (user_id, total_amount, total, status, order_date, created_at)
SELECT 
    u.id,
    5000.00,
    5000.00,
    'COMPLETED',
    NOW() - (70 + generate_series * 10 || ' days')::INTERVAL,
    NOW() - (70 + generate_series * 10 || ' days')::INTERVAL
FROM mentee_power.users u
CROSS JOIN generate_series(0, 2)
WHERE u.email = 'premium@test.com';

-- Заказы для Product B (Regular клиент) - 3 заказа по 5000 = 15000 (REGULAR: >= 2 заказов, >= 5000, но < 5 заказов или < 20000)
INSERT INTO mentee_power.orders (user_id, total_amount, total, status, order_date, created_at)
SELECT 
    u.id,
    5000.00,
    5000.00,
    'COMPLETED',
    NOW() - (90 + generate_series * 10 || ' days')::INTERVAL,
    NOW() - (90 + generate_series * 10 || ' days')::INTERVAL
FROM mentee_power.users u
CROSS JOIN generate_series(0, 2)
WHERE u.email = 'regular@test.com';

-- Заказы для NEW клиента - 1 заказ (NEW: >= 1 заказ)
-- Используем отдельный продукт для NEW клиента, чтобы не влиять на ABC анализ
INSERT INTO mentee_power.orders (user_id, total_amount, total, status, order_date, created_at)
SELECT 
    u.id,
    2000.00,
    2000.00,
    'COMPLETED',
    NOW() - (10 || ' days')::INTERVAL,
    NOW() - (10 || ' days')::INTERVAL
FROM mentee_power.users u
WHERE u.email = 'new@test.com';

-- Заказы для Product C (VIP клиент) - 5 заказов по 2000 = 10000
INSERT INTO mentee_power.orders (user_id, total_amount, total, status, order_date, created_at)
SELECT 
    u.id,
    2000.00,
    2000.00,
    'COMPLETED',
    NOW() - (30 + generate_series * 10 || ' days')::INTERVAL,
    NOW() - (30 + generate_series * 10 || ' days')::INTERVAL
FROM mentee_power.users u
CROSS JOIN generate_series(0, 4)
WHERE u.email = 'vip@test.com';

-- Позиции заказов для Product A (от VIP и Premium клиентов)
INSERT INTO mentee_power.order_items (order_id, product_id, quantity, price, created_at)
SELECT 
    o.id,
    (SELECT id FROM mentee_power.products WHERE name = 'High Revenue Product A' LIMIT 1),
    1,
    10000.00,
    o.created_at
FROM mentee_power.orders o
WHERE o.user_id IN (SELECT id FROM mentee_power.users WHERE email IN ('vip@test.com', 'premium@test.com'))
  AND o.total_amount = 10000.00
  AND NOT EXISTS (
      SELECT 1 FROM mentee_power.order_items oi 
      WHERE oi.order_id = o.id
  );

-- Позиции заказов для Product B (от Regular и Premium клиентов)
INSERT INTO mentee_power.order_items (order_id, product_id, quantity, price, created_at)
SELECT 
    o.id,
    (SELECT id FROM mentee_power.products WHERE name = 'Medium Revenue Product B' LIMIT 1),
    1,
    5000.00,
    o.created_at
FROM mentee_power.orders o
WHERE o.user_id IN (SELECT id FROM mentee_power.users WHERE email IN ('regular@test.com', 'premium@test.com'))
  AND o.total_amount = 5000.00
  AND NOT EXISTS (
      SELECT 1 FROM mentee_power.order_items oi 
      WHERE oi.order_id = o.id
  );

-- Позиции заказов для NEW клиента (используем Product C, но это не влияет на ABC анализ, так как NEW клиент имеет только 1 заказ)
INSERT INTO mentee_power.order_items (order_id, product_id, quantity, price, created_at)
SELECT 
    o.id,
    (SELECT id FROM mentee_power.products WHERE name = 'Low Revenue Product C' LIMIT 1),
    1,
    2000.00,
    o.created_at
FROM mentee_power.orders o
WHERE o.user_id IN (SELECT id FROM mentee_power.users WHERE email = 'new@test.com')
  AND o.total_amount = 2000.00
  AND NOT EXISTS (
      SELECT 1 FROM mentee_power.order_items oi 
      WHERE oi.order_id = o.id
  );

-- Позиции заказов для Product C
INSERT INTO mentee_power.order_items (order_id, product_id, quantity, price, created_at)
SELECT 
    o.id,
    (SELECT id FROM mentee_power.products WHERE name = 'Low Revenue Product C' LIMIT 1),
    1,
    2000.00,
    o.created_at
FROM mentee_power.orders o
WHERE o.user_id IN (SELECT id FROM mentee_power.users WHERE email = 'vip@test.com')
  AND o.total_amount = 2000.00
  AND NOT EXISTS (
      SELECT 1 FROM mentee_power.order_items oi 
      WHERE oi.order_id = o.id
  );

--rollback DELETE FROM mentee_power.order_items WHERE order_id IN (SELECT id FROM mentee_power.orders WHERE user_id IN (SELECT id FROM mentee_power.users WHERE email IN ('vip@test.com', 'premium@test.com', 'regular@test.com', 'new@test.com', 'inactive@test.com'))); DELETE FROM mentee_power.orders WHERE user_id IN (SELECT id FROM mentee_power.users WHERE email IN ('vip@test.com', 'premium@test.com', 'regular@test.com', 'new@test.com', 'inactive@test.com')); DELETE FROM mentee_power.products WHERE name IN ('High Revenue Product A', 'Medium Revenue Product B', 'Low Revenue Product C'); DELETE FROM mentee_power.users WHERE email IN ('vip@test.com', 'premium@test.com', 'regular@test.com', 'new@test.com', 'inactive@test.com'); DELETE FROM mentee_power.categories WHERE description IS NOT NULL AND description IN ('Electronic devices and accessories', 'Apparel and fashion items', 'Home improvement and garden supplies', 'Portable computers', 'Mobile phones and accessories', 'Tablet computers', 'Clothing for men', 'Clothing for women', 'High-performance gaming laptops', 'Professional laptops for business');
