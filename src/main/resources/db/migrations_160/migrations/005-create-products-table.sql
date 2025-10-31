--liquibase formatted sql
--changeset developer-1:005-create-products-table
--comment: Создание таблицы товаров

-- Создание таблицы товаров
CREATE TABLE mentee_power.products (
                                       id BIGSERIAL PRIMARY KEY,
                                       name VARCHAR(200) NOT NULL,
                                       description TEXT,
                                       price DECIMAL(10,2) NOT NULL,
                                       category VARCHAR(100) NOT NULL,
                                       in_stock BOOLEAN DEFAULT true NOT NULL,
                                       created_at TIMESTAMP DEFAULT NOW() NOT NULL
);

-- Индексы
CREATE INDEX idx_products_category ON mentee_power.products(category);
CREATE INDEX idx_products_in_stock ON mentee_power.products(in_stock);
CREATE INDEX idx_products_name ON mentee_power.products(name);

-- Check constraints
ALTER TABLE mentee_power.products
    ADD CONSTRAINT check_price_positive
        CHECK (price > 0);

-- Комментарии
COMMENT ON TABLE mentee_power.products IS 'Товары интернет-магазина';
COMMENT ON COLUMN mentee_power.products.in_stock IS 'Наличие товара на складе';

--rollback DROP TABLE mentee_power.products;