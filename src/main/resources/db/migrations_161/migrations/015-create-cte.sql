-- src/main/resources/db/migrations/009-create-advanced-analytics-schema.sql
--liquibase formatted sql
--changeset mp172:create-advanced-analytics-schema
--context: !test

-- Организационная иерархия
CREATE TABLE IF NOT EXISTS mentee_power.organizations (
                               id BIGSERIAL PRIMARY KEY,
                               name VARCHAR(200) NOT NULL,
                               type VARCHAR(50) NOT NULL,
                               parent_id BIGINT REFERENCES mentee_power.organizations(id),
                               level INTEGER DEFAULT 0,
                               is_active BOOLEAN DEFAULT true,
                               created_at TIMESTAMP DEFAULT NOW()
);

-- Сотрудники с привязкой к иерархии
CREATE TABLE IF NOT EXISTS mentee_power.employees (
                           id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(100) NOT NULL,
                           email VARCHAR(100) UNIQUE NOT NULL,
                           manager_id BIGINT REFERENCES mentee_power.employees(id),
                           organization_id BIGINT NOT NULL REFERENCES mentee_power.organizations(id),
                           position VARCHAR(100),
                           hire_date DATE NOT NULL,
                           salary DECIMAL(10,2),
                           status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- Территории продаж
CREATE TABLE IF NOT EXISTS mentee_power.sales_territories (
                                   id BIGSERIAL PRIMARY KEY,
                                   name VARCHAR(100) NOT NULL,
                                   region VARCHAR(50),
                                   country VARCHAR(50),
                                   manager_id BIGINT REFERENCES mentee_power.employees(id),
                                   is_active BOOLEAN DEFAULT true
);

-- Расширение таблицы клиентов (только если таблица существует)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'mentee_power' AND table_name = 'customers') THEN
        ALTER TABLE mentee_power.customers ADD COLUMN IF NOT EXISTS territory_id BIGINT REFERENCES mentee_power.sales_territories(id);
        ALTER TABLE mentee_power.customers ADD COLUMN IF NOT EXISTS first_purchase_date DATE;
        ALTER TABLE mentee_power.customers ADD COLUMN IF NOT EXISTS lifetime_value DECIMAL(10,2) DEFAULT 0.00;
    END IF;
END $$;

-- Привязка заказов к продавцам
ALTER TABLE mentee_power.orders ADD COLUMN IF NOT EXISTS sales_rep_id BIGINT REFERENCES mentee_power.employees(id);

-- Индексы для оптимизации сложных запросов
CREATE INDEX IF NOT EXISTS idx_organizations_parent_level ON mentee_power.organizations(parent_id, level);
CREATE INDEX IF NOT EXISTS idx_employees_manager_org ON mentee_power.employees(manager_id, organization_id);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'mentee_power' AND table_name = 'customers') 
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'mentee_power' AND table_name = 'customers' AND column_name = 'segment') THEN
        CREATE INDEX IF NOT EXISTS idx_customers_territory_segment ON mentee_power.customers(territory_id, segment);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'mentee_power' AND table_name = 'orders' AND column_name = 'order_date') THEN
        CREATE INDEX IF NOT EXISTS idx_orders_date_rep ON mentee_power.orders(order_date, sales_rep_id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'mentee_power' AND table_name = 'orders' AND column_name = 'customer_id') THEN
        CREATE INDEX IF NOT EXISTS idx_orders_customer_date ON mentee_power.orders(customer_id, order_date);
    END IF;
END $$;

-- Тестовые данные для иерархии (только если таблица пустая)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM mentee_power.organizations LIMIT 1) THEN
        INSERT INTO mentee_power.organizations (name, type, parent_id, level) VALUES
                                                             ('MenteePower Corp', 'CORPORATION', NULL, 0),
                                                             ('Sales Division', 'DIVISION', 1, 1),
                                                             ('Marketing Division', 'DIVISION', 1, 1),
                                                             ('North Region', 'REGION', 2, 2),
                                                             ('South Region', 'REGION', 2, 2),
                                                             ('Moscow Office', 'OFFICE', 4, 3),
                                                             ('SPB Office', 'OFFICE', 4, 3),
                                                             ('Kazan Office', 'OFFICE', 5, 3);
    END IF;
END $$;

--rollback DROP TABLE IF EXISTS mentee_power.sales_territories; DROP TABLE IF EXISTS mentee_power.employees; DROP TABLE IF EXISTS mentee_power.organizations;