--liquibase formatted sql
--changeset mp168:create-deadlock-test-data

CREATE TABLE IF NOT EXISTS mentee_power.users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    last_login TIMESTAMP,
    city VARCHAR(50),
    registration_date TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT true
);

CREATE TABLE IF NOT EXISTS mentee_power.products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    sku VARCHAR(50) UNIQUE,
    stock_quantity INTEGER DEFAULT 0,
    last_stock_update TIMESTAMP,
    category_id BIGINT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mentee_power.orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES mentee_power.users(id),
    total DECIMAL(10,2) NOT NULL,
    total_amount DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW(),
    region VARCHAR(50) DEFAULT 'MOSCOW'
);


UPDATE mentee_power.orders SET total_amount = total WHERE total_amount IS NULL AND total IS NOT NULL;


CREATE TABLE IF NOT EXISTS mentee_power.order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES mentee_power.orders(id),
    product_id BIGINT NOT NULL REFERENCES mentee_power.products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'PENDING'
);


UPDATE mentee_power.order_items SET unit_price = price WHERE unit_price IS NULL AND price IS NOT NULL;


ALTER TABLE mentee_power.accounts 
    ADD COLUMN IF NOT EXISTS last_updated TIMESTAMP DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';


CREATE TABLE IF NOT EXISTS mentee_power.transfers (
    id BIGSERIAL PRIMARY KEY,
    from_account_id BIGINT NOT NULL REFERENCES mentee_power.accounts(id),
    to_account_id BIGINT NOT NULL REFERENCES mentee_power.accounts(id),
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(20) DEFAULT 'PENDING',
    transfer_date TIMESTAMP DEFAULT NOW(),
    description TEXT,
    CONSTRAINT different_accounts CHECK (from_account_id != to_account_id)
);


CREATE TABLE IF NOT EXISTS mentee_power.inventory_locks (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES mentee_power.products(id),
    order_id BIGINT REFERENCES mentee_power.orders(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    lock_type VARCHAR(20) NOT NULL DEFAULT 'RESERVATION',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    locked_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,
    CONSTRAINT valid_lock_type CHECK (lock_type IN ('RESERVATION', 'HOLD', 'PURCHASE'))
);


CREATE INDEX IF NOT EXISTS idx_users_email ON mentee_power.users(email);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON mentee_power.users(is_active);
CREATE INDEX IF NOT EXISTS idx_products_sku ON mentee_power.products(sku);
CREATE INDEX IF NOT EXISTS idx_products_stock ON mentee_power.products(stock_quantity);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON mentee_power.orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON mentee_power.orders(status);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON mentee_power.order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON mentee_power.order_items(product_id);
CREATE INDEX IF NOT EXISTS idx_order_items_status ON mentee_power.order_items(status);
CREATE INDEX IF NOT EXISTS idx_transfers_from_account ON mentee_power.transfers(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transfers_to_account ON mentee_power.transfers(to_account_id);
CREATE INDEX IF NOT EXISTS idx_transfers_status ON mentee_power.transfers(status);
CREATE INDEX IF NOT EXISTS idx_inventory_locks_product ON mentee_power.inventory_locks(product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_locks_order ON mentee_power.inventory_locks(order_id);
CREATE INDEX IF NOT EXISTS idx_inventory_locks_status ON mentee_power.inventory_locks(status);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON mentee_power.accounts(status);


TRUNCATE mentee_power.accounts, mentee_power.transfers, mentee_power.orders, mentee_power.order_items, mentee_power.products, mentee_power.inventory_locks, mentee_power.users RESTART IDENTITY CASCADE;

-- Пользователи для тестирования
INSERT INTO mentee_power.users (email, first_name, last_name, last_login, is_active, name) VALUES
    ('alice@example.com', 'Alice', 'Johnson', NOW() - interval '1 hour', true, 'Alice Johnson'),
    ('bob@example.com', 'Bob', 'Smith', NOW() - interval '2 hours', true, 'Bob Smith'),
    ('charlie@example.com', 'Charlie', 'Brown', NOW() - interval '30 minutes', true, 'Charlie Brown'),
    ('diana@example.com', 'Diana', 'Wilson', NOW() - interval '15 minutes', true, 'Diana Wilson');

-- Счета с балансами для трансферов
INSERT INTO mentee_power.accounts (user_id, balance, account_type, last_updated, status, updated_at) VALUES
    (1, 5000.00, 'CHECKING', NOW(), 'ACTIVE', NOW()),
    (2, 3000.00, 'CHECKING', NOW(), 'ACTIVE', NOW()),
    (3, 8000.00, 'SAVINGS', NOW(), 'ACTIVE', NOW()),
    (4, 2500.00, 'CHECKING', NOW(), 'ACTIVE', NOW());

-- Товары с ограниченным количеством для inventory conflicts
INSERT INTO mentee_power.products (name, sku, stock_quantity, price, last_stock_update) VALUES
    ('Limited GPU RTX 4090', 'GPU-RTX4090-001', 5, 1599.99, NOW()),
    ('Gaming Console PS5', 'CONSOLE-PS5-001', 3, 499.99, NOW()),
    ('Rare Collectible', 'COLLECTIBLE-001', 1, 999.99, NOW()),
    ('Premium Laptop', 'LAPTOP-PREMIUM-001', 2, 2999.99, NOW()),
    ('Smartphone iPhone 15', 'PHONE-IP15-001', 4, 1199.99, NOW());

-- Заказы для тестирования FK дедлоков
INSERT INTO mentee_power.orders (user_id, total_amount, status, created_at, total) VALUES
    (1, 1599.99, 'PENDING', NOW() - interval '1 hour', 1599.99),
    (2, 499.99, 'CONFIRMED', NOW() - interval '30 minutes', 499.99),
    (3, 999.99, 'PROCESSING', NOW() - interval '15 minutes', 999.99),
    (4, 2999.99, 'PENDING', NOW() - interval '5 minutes', 2999.99);

-- Позиции заказов
INSERT INTO mentee_power.order_items (order_id, product_id, quantity, unit_price, status, price) VALUES
    (1, 1, 1, 1599.99, 'RESERVED', 1599.99),
    (2, 2, 1, 499.99, 'CONFIRMED', 499.99),
    (3, 3, 1, 999.99, 'PROCESSING', 999.99),
    (4, 4, 1, 2999.99, 'PENDING', 2999.99);

--rollback TRUNCATE mentee_power.accounts, mentee_power.transfers, mentee_power.orders, mentee_power.order_items, mentee_power.products, mentee_power.inventory_locks, mentee_power.users RESTART IDENTITY CASCADE;