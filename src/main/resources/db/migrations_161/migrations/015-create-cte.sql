-- src/main/resources/db/migrations/009-create-advanced-analytics-schema.sql
--liquibase formatted sql
--changeset mp172:create-advanced-analytics-schema

-- Организационная иерархия
CREATE TABLE organizations (
                               id BIGSERIAL PRIMARY KEY,
                               name VARCHAR(200) NOT NULL,
                               type VARCHAR(50) NOT NULL,
                               parent_id BIGINT REFERENCES organizations(id),
                               level INTEGER DEFAULT 0,
                               is_active BOOLEAN DEFAULT true,
                               created_at TIMESTAMP DEFAULT NOW()
);

-- Сотрудники с привязкой к иерархии
CREATE TABLE employees (
                           id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(100) NOT NULL,
                           email VARCHAR(100) UNIQUE NOT NULL,
                           manager_id BIGINT REFERENCES employees(id),
                           organization_id BIGINT NOT NULL REFERENCES organizations(id),
                           position VARCHAR(100),
                           hire_date DATE NOT NULL,
                           salary DECIMAL(10,2),
                           status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- Территории продаж
CREATE TABLE sales_territories (
                                   id BIGSERIAL PRIMARY KEY,
                                   name VARCHAR(100) NOT NULL,
                                   region VARCHAR(50),
                                   country VARCHAR(50),
                                   manager_id BIGINT REFERENCES employees(id),
                                   is_active BOOLEAN DEFAULT true
);

-- Расширение таблицы клиентов
ALTER TABLE customers ADD COLUMN IF NOT EXISTS territory_id BIGINT REFERENCES sales_territories(id);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS first_purchase_date DATE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS lifetime_value DECIMAL(10,2) DEFAULT 0.00;

-- Привязка заказов к продавцам
ALTER TABLE orders ADD COLUMN IF NOT EXISTS sales_rep_id BIGINT REFERENCES employees(id);

-- Индексы для оптимизации сложных запросов
CREATE INDEX IF NOT EXISTS idx_organizations_parent_level ON organizations(parent_id, level);
CREATE INDEX IF NOT EXISTS idx_employees_manager_org ON employees(manager_id, organization_id);
CREATE INDEX IF NOT EXISTS idx_customers_territory_segment ON customers(territory_id, segment);
CREATE INDEX IF NOT EXISTS idx_orders_date_rep ON orders(order_date, sales_rep_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer_date ON orders(customer_id, order_date);

-- Тестовые данные для иерархии
INSERT INTO organizations (name, type, parent_id, level) VALUES
                                                             ('MenteePower Corp', 'CORPORATION', NULL, 0),
                                                             ('Sales Division', 'DIVISION', 1, 1),
                                                             ('Marketing Division', 'DIVISION', 1, 1),
                                                             ('North Region', 'REGION', 2, 2),
                                                             ('South Region', 'REGION', 2, 2),
                                                             ('Moscow Office', 'OFFICE', 4, 3),
                                                             ('SPB Office', 'OFFICE', 4, 3),
                                                             ('Kazan Office', 'OFFICE', 5, 3);

--rollback DROP TABLE sales_territories; DROP TABLE employees; DROP TABLE organizations; ALTER TABLE customers DROP COLUMN territory_id; ALTER TABLE customers DROP COLUMN first_purchase_date; ALTER TABLE customers DROP COLUMN lifetime_value; ALTER TABLE orders DROP COLUMN sales_rep_id;