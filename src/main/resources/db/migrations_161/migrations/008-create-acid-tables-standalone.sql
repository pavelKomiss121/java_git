--liquibase formatted sql
--changeset mp165:create-acid-tables-standalone

-- Создаем схему
CREATE SCHEMA IF NOT EXISTS mentee_power;
SET search_path TO mentee_power;

-- Удаляем существующие таблицы, если нужно (для полной пересоздания)
DROP TABLE IF EXISTS mentee_power.transactions CASCADE;
DROP TABLE IF EXISTS mentee_power.accounts CASCADE;
DROP TABLE IF EXISTS mentee_power.order_items CASCADE;
DROP TABLE IF EXISTS mentee_power.orders CASCADE;
DROP TABLE IF EXISTS mentee_power.products CASCADE;
DROP TABLE IF EXISTS mentee_power.categories CASCADE;
DROP TABLE IF EXISTS mentee_power.users CASCADE;

-- Создаем таблицу users
CREATE TABLE mentee_power.users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    city VARCHAR(50),
    registration_date TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT true
);

-- Создаем таблицу categories
CREATE TABLE mentee_power.categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT REFERENCES mentee_power.categories(id)
);

-- Создаем таблицу products с stock_quantity и status
CREATE TABLE mentee_power.products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    category_id BIGINT REFERENCES mentee_power.categories(id),
    sku VARCHAR(50) UNIQUE,
    stock_quantity INTEGER DEFAULT 0,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW()
);

-- Создаем таблицу accounts
CREATE TABLE mentee_power.accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES mentee_power.users(id),
    account_type VARCHAR(50) NOT NULL DEFAULT 'CHECKING',
    balance DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'RUB',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT true
);

-- Создаем таблицу orders с payment_account_id
CREATE TABLE mentee_power.orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES mentee_power.users(id),
    payment_account_id BIGINT REFERENCES mentee_power.accounts(id),
    total DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW(),
    confirmed_at TIMESTAMP,
    shipped_at TIMESTAMP,
    region VARCHAR(50) DEFAULT 'MOSCOW'
);

-- Создаем таблицу order_items с total_price и status
CREATE TABLE mentee_power.order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES mentee_power.orders(id),
    product_id BIGINT NOT NULL REFERENCES mentee_power.products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2),
    status VARCHAR(50) DEFAULT 'PENDING'
);

-- Создаем таблицу transactions
CREATE TABLE mentee_power.transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(255) UNIQUE NOT NULL,
    from_account_id BIGINT REFERENCES mentee_power.accounts(id),
    to_account_id BIGINT REFERENCES mentee_power.accounts(id),
    amount DECIMAL(10,2) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    processed_at TIMESTAMP,
    metadata JSONB,
    error_message TEXT
);

-- Создаем индексы для производительности
CREATE INDEX idx_accounts_user_id ON mentee_power.accounts(user_id);
CREATE INDEX idx_accounts_is_active ON mentee_power.accounts(is_active);
CREATE INDEX idx_transactions_from_account_id ON mentee_power.transactions(from_account_id);
CREATE INDEX idx_transactions_to_account_id ON mentee_power.transactions(to_account_id);
CREATE INDEX idx_transactions_transaction_id ON mentee_power.transactions(transaction_id);
CREATE INDEX idx_transactions_status ON mentee_power.transactions(status);
CREATE INDEX idx_transactions_created_at ON mentee_power.transactions(created_at);
CREATE INDEX idx_orders_payment_account_id ON mentee_power.orders(payment_account_id);
CREATE INDEX idx_orders_user_id ON mentee_power.orders(user_id);
CREATE INDEX idx_products_sku ON mentee_power.products(sku);
CREATE INDEX idx_users_email ON mentee_power.users(email);

-- Добавляем тестовые данные
INSERT INTO mentee_power.users (name, email, city, is_active) VALUES
    ('Иван Иванов', 'ivan@example.com', 'Moscow', true),
    ('Мария Петрова', 'maria@example.com', 'Saint Petersburg', true),
    ('Алексей Сидоров', 'alex@example.com', 'Novosibirsk', true);

INSERT INTO mentee_power.categories (name) VALUES
    ('Electronics'),
    ('Books'),
    ('Clothing');

INSERT INTO mentee_power.products (name, description, price, category_id, sku, stock_quantity, status) VALUES
    ('Ноутбук', 'Мощный ноутбук для работы', 50000.00, 1, 'LAPTOP-001', 10, 'ACTIVE'),
    ('Смартфон', 'Современный смартфон', 30000.00, 1, 'PHONE-001', 20, 'ACTIVE'),
    ('Книга по Java', 'Учебник по программированию', 1500.00, 2, 'BOOK-001', 50, 'ACTIVE'),
    ('Футболка', 'Хлопковая футболка', 1000.00, 3, 'TSHIRT-001', 100, 'ACTIVE');

INSERT INTO mentee_power.accounts (user_id, account_type, balance, currency, is_active) VALUES
    (1, 'CHECKING', 10000.00, 'RUB', true),
    (2, 'CHECKING', 5000.00, 'RUB', true),
    (3, 'CHECKING', 15000.00, 'RUB', true),
    (1, 'SAVINGS', 50000.00, 'RUB', true);

--rollback DROP SCHEMA IF EXISTS mentee_power CASCADE;

