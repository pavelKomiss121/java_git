--liquibase formatted sql
--changeset mp163:create-composite-indexes

-- Составной индекс для поиска заказов по пользователю и статусу
CREATE INDEX idx_orders_user_status ON mentee_power.orders(user_id, status);

-- Составной индекс для товаров по категории и цене
CREATE INDEX idx_products_category_price ON mentee_power.products(category_id, price)
    WHERE is_active = true;

-- Функциональный индекс для регистронезависимого поиска
CREATE UNIQUE INDEX idx_users_email_lower ON mentee_power.users(LOWER(email))
    WHERE is_active = true;

-- Частичный индекс для активных товаров с высокой ценой
CREATE INDEX idx_products_expensive_active ON mentee_power.products(category_id, price DESC)
    WHERE is_active = true AND price > 1000;

-- Составной индекс для заказов по статусу и дате
CREATE INDEX idx_orders_status_created ON mentee_power.orders(status, created_at);

-- Обновляем статистику
ANALYZE mentee_power.orders;
ANALYZE mentee_power.products;
ANALYZE mentee_power.users;

--rollback DROP INDEX IF EXISTS idx_orders_user_status; DROP INDEX IF EXISTS idx_products_category_price; DROP INDEX IF EXISTS idx_users_email_lower; DROP INDEX IF EXISTS idx_products_expensive_active; DROP INDEX IF EXISTS idx_orders_status_created;