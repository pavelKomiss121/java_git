--liquibase formatted sql
--changeset mp163:create-performance-indexes

-- B-Tree индексы для частых запросов
CREATE INDEX idx_users_email_btree ON mentee_power.users(email);
CREATE INDEX idx_users_created_at ON mentee_power.users(created_at);
CREATE INDEX idx_orders_user_id_status ON mentee_power.orders(user_id, status);
CREATE INDEX idx_orders_created_at_total ON mentee_power.orders(created_at, total);

-- Составные индексы для сложных запросов
CREATE INDEX idx_order_items_order_product ON mentee_power.order_items(order_id, product_id);
CREATE INDEX idx_products_category_price ON mentee_power.products(category_id, price DESC);

-- Частичные индексы для активных записей
CREATE INDEX idx_users_active_email ON mentee_power.users(email) WHERE is_active = true;
CREATE INDEX idx_orders_pending_created ON mentee_power.orders(created_at) WHERE status = 'PENDING';

-- Hash индекс для точного поиска
CREATE INDEX idx_products_sku_hash ON mentee_power.products USING HASH(sku) WHERE sku IS NOT NULL;

-- Функциональный индекс для поиска без учета регистра
CREATE INDEX idx_users_email_lower ON mentee_power.users(LOWER(email));

-- GIN индекс для полнотекстового поиска
CREATE INDEX idx_products_search_gin ON mentee_power.products
    USING GIN(to_tsvector('russian', name || ' ' || COALESCE(description, '')));

--rollback DROP INDEX IF EXISTS idx_users_email_btree; DROP INDEX IF EXISTS idx_users_created_at; DROP INDEX IF EXISTS idx_orders_user_id_status; DROP INDEX IF EXISTS idx_orders_created_at_total; DROP INDEX IF EXISTS idx_order_items_order_product; DROP INDEX IF EXISTS idx_products_category_price; DROP INDEX IF EXISTS idx_users_active_email; DROP INDEX IF EXISTS idx_orders_pending_created; DROP INDEX IF EXISTS idx_products_sku_hash; DROP INDEX IF EXISTS idx_users_email_lower; DROP INDEX IF EXISTS idx_products_search_gin;