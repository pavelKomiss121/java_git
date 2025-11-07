--liquibase formatted sql
--changeset mp171:create-cte-analytics-schema

-- Расширение существующих таблиц
ALTER TABLE mentee_power.users ADD COLUMN IF NOT EXISTS customer_segment VARCHAR(20) DEFAULT 'NEW';
ALTER TABLE mentee_power.users ADD COLUMN IF NOT EXISTS lifetime_value DECIMAL(10,2) DEFAULT 0.00;

ALTER TABLE mentee_power.orders ADD COLUMN IF NOT EXISTS order_date DATE;
ALTER TABLE mentee_power.orders ADD COLUMN IF NOT EXISTS shipped_date DATE;
ALTER TABLE mentee_power.orders ADD COLUMN IF NOT EXISTS delivered_date DATE;
ALTER TABLE mentee_power.orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) DEFAULT 'CARD';

-- Обновляем order_date из created_at если он NULL
UPDATE mentee_power.orders SET order_date = DATE(created_at) WHERE order_date IS NULL;

ALTER TABLE mentee_power.order_items ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();
ALTER TABLE mentee_power.order_items ADD COLUMN IF NOT EXISTS discount DECIMAL(10,2) DEFAULT 0.00;

ALTER TABLE mentee_power.categories ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE mentee_power.categories ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;

-- Индексы для оптимизации CTE запросов
CREATE INDEX IF NOT EXISTS idx_users_registration_segment ON mentee_power.users(registration_date, customer_segment);
CREATE INDEX IF NOT EXISTS idx_orders_user_date ON mentee_power.orders(user_id, order_date);
CREATE INDEX IF NOT EXISTS idx_orders_status_date ON mentee_power.orders(status, order_date);
CREATE INDEX IF NOT EXISTS idx_order_items_created_product ON mentee_power.order_items(created_at, product_id);
CREATE INDEX IF NOT EXISTS idx_categories_parent_active ON mentee_power.categories(parent_id, is_active);

--rollback ALTER TABLE mentee_power.users DROP COLUMN IF EXISTS customer_segment; ALTER TABLE mentee_power.users DROP COLUMN IF EXISTS lifetime_value; ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS order_date; ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS shipped_date; ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS delivered_date; ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS payment_method; ALTER TABLE mentee_power.order_items DROP COLUMN IF EXISTS created_at; ALTER TABLE mentee_power.order_items DROP COLUMN IF EXISTS discount; ALTER TABLE mentee_power.categories DROP COLUMN IF EXISTS description; ALTER TABLE mentee_power.categories DROP COLUMN IF EXISTS is_active; DROP INDEX IF EXISTS mentee_power.idx_users_registration_segment; DROP INDEX IF EXISTS mentee_power.idx_orders_user_date; DROP INDEX IF EXISTS mentee_power.idx_orders_status_date; DROP INDEX IF EXISTS mentee_power.idx_order_items_created_product; DROP INDEX IF EXISTS mentee_power.idx_categories_parent_active;

