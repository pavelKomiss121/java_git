--liquibase formatted sql
--changeset mp161:generate-massive-test-data

-- Генерация 50,000 пользователей для демонстрации производительности
INSERT INTO users (name, email, city, registration_date, is_active)
SELECT
    'User ' || generate_series,
    'user' || generate_series || '@example.com',
    CASE (random() * 4)::int
        WHEN 0 THEN 'Moscow'
        WHEN 1 THEN 'SPB'
        WHEN 2 THEN 'Ekaterinburg'
        WHEN 3 THEN 'Novosibirsk'
        ELSE 'Kazan'
        END,
    NOW() - (random() * interval '365 days'),
    random() > 0.1  -- 90% активных пользователей
FROM generate_series(1, 50000);

-- Иерархия категорий
INSERT INTO categories (name, parent_id) VALUES
                                             ('Электроника', NULL),
                                             ('Книги', NULL),
                                             ('Одежда', NULL),
                                             ('Ноутбуки', 1),
                                             ('Смартфоны', 1),
                                             ('Планшеты', 1),
                                             ('Художественная литература', 2),
                                             ('Техническая литература', 2),
                                             ('Мужская одежда', 3),
                                             ('Женская одежда', 3);

-- 100,000 товаров
INSERT INTO products (name, description, price, category_id, sku, created_at)
SELECT
    'Product ' || generate_series,
    'Description for product ' || generate_series || ' with lots of details about features and benefits',
    (random() * 99999 + 1)::decimal(10,2),
    (random() * 10 + 1)::bigint,
    'SKU-' || LPAD(generate_series::text, 8, '0'),
    NOW() - (random() * interval '365 days')
FROM generate_series(1, 100000);

-- 200,000 заказов
INSERT INTO orders (user_id, total, status, created_at, region)
SELECT
    (random() * 50000 + 1)::bigint,
    (random() * 5000 + 10)::decimal(10,2),
    CASE (random() * 4)::int
        WHEN 0 THEN 'PENDING'
        WHEN 1 THEN 'CONFIRMED'
        WHEN 2 THEN 'SHIPPED'
        ELSE 'DELIVERED'
        END,
    NOW() - (random() * interval '365 days'),
    CASE (random() * 4)::int
        WHEN 0 THEN 'MOSCOW'
        WHEN 1 THEN 'SPB'
        WHEN 2 THEN 'EKATERINBURG'
        WHEN 3 THEN 'NOVOSIBIRSK'
        ELSE 'KAZAN'
        END
FROM generate_series(1, 200000);

-- 800,000 позиций в заказах
INSERT INTO order_items (order_id, product_id, quantity, price)
SELECT
    (random() * 200000 + 1)::bigint,
    (random() * 100000 + 1)::bigint,
    (random() * 5 + 1)::int,
    (random() * 999 + 1)::decimal(10,2)
FROM generate_series(1, 800000);

-- Обновляем статистику для точных планов выполнения
ANALYZE users;
ANALYZE categories;
ANALYZE products;
ANALYZE orders;
ANALYZE order_items;

--rollback DELETE FROM order_items; DELETE FROM orders; DELETE FROM products; DELETE FROM categories; DELETE FROM users WHERE id > 0;