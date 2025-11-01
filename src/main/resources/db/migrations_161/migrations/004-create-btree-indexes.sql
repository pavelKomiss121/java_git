--liquibase formatted sql
--changeset mp162:create-btree-indexes

-- Уникальный индекс для аутентификации пользователей
CREATE UNIQUE INDEX idx_users_email_active ON users(email) WHERE is_active = true;

-- Составной индекс для загрузки заказов пользователя
CREATE INDEX idx_orders_user_created_desc ON orders(user_id, created_at DESC);

-- Индекс для поиска заказов по статусу
CREATE INDEX idx_orders_status ON orders(status);

-- Составной индекс для фильтрации товаров по категории и цене
CREATE INDEX idx_products_category_price ON products(category_id, price);

-- Индекс для поиска по SKU
CREATE UNIQUE INDEX idx_products_sku ON products(sku);

-- Индекс для временных диапазонов
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_users_registration_date ON users(registration_date);

-- Обновляем статистику
ANALYZE users;
ANALYZE orders;
ANALYZE products;
ANALYZE categories;

--rollback DROP INDEX IF EXISTS idx_users_email_active; DROP INDEX IF EXISTS idx_orders_user_created_desc; DROP INDEX IF EXISTS idx_orders_status; DROP INDEX IF EXISTS idx_products_category_price; DROP INDEX IF EXISTS idx_products_sku; DROP INDEX IF EXISTS idx_orders_created_at; DROP INDEX IF EXISTS idx_users_registration_date;