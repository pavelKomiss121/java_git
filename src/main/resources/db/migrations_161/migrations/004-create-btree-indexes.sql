--liquibase formatted sql
--changeset mp162:create-btree-indexes

-- Уникальный индекс для аутентификации пользователей
CREATE UNIQUE INDEX idx_users_email_active ON mentee_power.users(email) WHERE is_active = true;

-- Составной индекс для загрузки заказов пользователя
CREATE INDEX idx_orders_user_created_desc ON mentee_power.orders(user_id, created_at DESC);

-- Индекс для поиска заказов по статусу
CREATE INDEX idx_orders_status ON mentee_power.orders(status);

-- Составной индекс для фильтрации товаров по категории и цене
CREATE INDEX idx_products_category_price ON mentee_power.products(category_id, price);

-- Индекс для поиска по SKU
CREATE UNIQUE INDEX idx_products_sku ON mentee_power.products(sku);

-- Индекс для временных диапазонов
CREATE INDEX idx_orders_created_at ON mentee_power.orders(created_at);
CREATE INDEX idx_users_registration_date ON mentee_power.users(registration_date);

-- Обновляем статистику
ANALYZE mentee_power.users;
ANALYZE mentee_power.orders;
ANALYZE mentee_power.products;
ANALYZE mentee_power.categories;

--rollback DROP INDEX IF EXISTS idx_users_email_active; DROP INDEX IF EXISTS idx_orders_user_created_desc; DROP INDEX IF EXISTS idx_orders_status; DROP INDEX IF EXISTS idx_products_category_price; DROP INDEX IF EXISTS idx_products_sku; DROP INDEX IF EXISTS idx_orders_created_at; DROP INDEX IF EXISTS idx_users_registration_date;