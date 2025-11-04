--liquibase formatted sql
--changeset mp165:create-acid-tables

-- Добавляем stock_quantity в products (идемпотентно)
ALTER TABLE mentee_power.products ADD COLUMN IF NOT EXISTS stock_quantity INTEGER DEFAULT 0;

-- Добавляем status в products (идемпотентно)
ALTER TABLE mentee_power.products ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'ACTIVE';

-- Создаем таблицу accounts
CREATE TABLE IF NOT EXISTS mentee_power.accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES mentee_power.users(id),
    account_type VARCHAR(50) NOT NULL DEFAULT 'CHECKING',
    balance DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'RUB',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT true
);

-- Создаем таблицу transactions
CREATE TABLE IF NOT EXISTS mentee_power.transactions (
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

-- Добавляем payment_account_id в orders (идемпотентно)
ALTER TABLE mentee_power.orders ADD COLUMN IF NOT EXISTS payment_account_id BIGINT REFERENCES mentee_power.accounts(id);

-- Добавляем confirmed_at в orders (идемпотентно)
ALTER TABLE mentee_power.orders ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;

-- Добавляем shipped_at в orders (идемпотентно)
ALTER TABLE mentee_power.orders ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMP;

-- Добавляем total_price в order_items (идемпотентно)
ALTER TABLE mentee_power.order_items ADD COLUMN IF NOT EXISTS total_price DECIMAL(10,2);

-- Вычисляем total_price из quantity * price для существующих записей
UPDATE mentee_power.order_items SET total_price = quantity * price WHERE total_price IS NULL;

-- Добавляем status в order_items (идемпотентно)
ALTER TABLE mentee_power.order_items ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'PENDING';

-- Создаем индексы для производительности
CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON mentee_power.accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_is_active ON mentee_power.accounts(is_active);
CREATE INDEX IF NOT EXISTS idx_transactions_from_account_id ON mentee_power.transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account_id ON mentee_power.transactions(to_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_transaction_id ON mentee_power.transactions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON mentee_power.transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON mentee_power.transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_payment_account_id ON mentee_power.orders(payment_account_id);

--rollback DROP INDEX IF EXISTS mentee_power.idx_orders_payment_account_id;
--rollback DROP INDEX IF EXISTS mentee_power.idx_transactions_created_at;
--rollback DROP INDEX IF EXISTS mentee_power.idx_transactions_status;
--rollback DROP INDEX IF EXISTS mentee_power.idx_transactions_transaction_id;
--rollback DROP INDEX IF EXISTS mentee_power.idx_transactions_to_account_id;
--rollback DROP INDEX IF EXISTS mentee_power.idx_transactions_from_account_id;
--rollback DROP INDEX IF EXISTS mentee_power.idx_accounts_is_active;
--rollback DROP INDEX IF EXISTS mentee_power.idx_accounts_user_id;
--rollback ALTER TABLE mentee_power.order_items DROP COLUMN IF EXISTS status;
--rollback ALTER TABLE mentee_power.order_items DROP COLUMN IF EXISTS total_price;
--rollback ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS shipped_at;
--rollback ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS confirmed_at;
--rollback ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS payment_account_id;
--rollback DROP TABLE IF EXISTS mentee_power.transactions;
--rollback DROP TABLE IF EXISTS mentee_power.accounts;
--rollback ALTER TABLE mentee_power.products DROP COLUMN IF EXISTS status;
--rollback ALTER TABLE mentee_power.products DROP COLUMN IF EXISTS stock_quantity;

