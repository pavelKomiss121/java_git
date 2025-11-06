-- src/main/resources/db/migrations/007-create-sales-analytics-schema.sql
--liquibase formatted sql
--changeset mp170:create-sales-analytics-schema

CREATE TABLE IF NOT EXISTS mentee_power.regions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(50) NOT NULL,
    manager_name VARCHAR(100),
    is_active BOOLEAN DEFAULT true
);

CREATE TABLE IF NOT EXISTS mentee_power.products_sales_analytics (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    cost DECIMAL(10,2) NOT NULL,
    is_active BOOLEAN DEFAULT true
);

CREATE TABLE IF NOT EXISTS mentee_power.sales_people (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    region_id BIGINT NOT NULL REFERENCES mentee_power.regions(id),
    hire_date DATE NOT NULL,
    base_salary DECIMAL(10,2) NOT NULL,
    commission_rate DECIMAL(5,4) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS mentee_power.sales_transactions (
    id BIGSERIAL PRIMARY KEY,
    salesperson_id BIGINT NOT NULL REFERENCES mentee_power.sales_people(id),
    product_id BIGINT NOT NULL REFERENCES mentee_power.products_sales_analytics(id),
    amount DECIMAL(10,2) NOT NULL,
    quantity INTEGER NOT NULL,
    sale_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    notes TEXT
);

-- Индексы для оптимизации оконных функций
CREATE INDEX IF NOT EXISTS idx_sales_transactions_date_amount ON mentee_power.sales_transactions(sale_date, amount);
CREATE INDEX IF NOT EXISTS idx_sales_transactions_salesperson_date ON mentee_power.sales_transactions(salesperson_id, sale_date);
CREATE INDEX IF NOT EXISTS idx_sales_transactions_product_date ON mentee_power.sales_transactions(product_id, sale_date);
CREATE INDEX IF NOT EXISTS idx_sales_people_region_id ON mentee_power.sales_people(region_id);

--rollback DROP TABLE IF EXISTS mentee_power.sales_transactions; DROP TABLE IF EXISTS mentee_power.sales_people; DROP TABLE IF EXISTS mentee_power.products_sales_analytics; DROP TABLE IF EXISTS mentee_power.regions;