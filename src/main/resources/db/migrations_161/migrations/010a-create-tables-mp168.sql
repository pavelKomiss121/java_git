--liquibase formatted sql
--changeset mp168:create-tables-mp168

-- Создание таблиц для тестирования

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

ALTER TABLE mentee_power.users 
    ADD COLUMN IF NOT EXISTS customer_tier VARCHAR(20) DEFAULT 'BRONZE',
    ADD COLUMN IF NOT EXISTS lifetime_value DECIMAL(10,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_purchase_date TIMESTAMP;

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

CREATE TABLE IF NOT EXISTS mentee_power.categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT REFERENCES mentee_power.categories(id),
    category_type VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS mentee_power.reviews (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES mentee_power.users(id),
    product_id BIGINT NOT NULL REFERENCES mentee_power.products(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    review_text TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    is_verified BOOLEAN DEFAULT false,
    helpful_votes INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS mentee_power.promotions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    promo_code VARCHAR(50) UNIQUE NOT NULL,
    promotion_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10,2),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    usage_limit INTEGER,
    is_active BOOLEAN DEFAULT true,
    CONSTRAINT valid_promotion_type CHECK (promotion_type IN ('PERCENTAGE', 'FIXED_AMOUNT'))
);

CREATE TABLE IF NOT EXISTS mentee_power.promotion_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES mentee_power.users(id),
    promotion_id BIGINT NOT NULL REFERENCES mentee_power.promotions(id),
    order_id BIGINT REFERENCES mentee_power.orders(id),
    discount_applied DECIMAL(10,2) NOT NULL,
    used_at TIMESTAMP DEFAULT NOW()
);

-- Создание индексов
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
CREATE INDEX IF NOT EXISTS idx_users_customer_tier ON mentee_power.users(customer_tier);
CREATE INDEX IF NOT EXISTS idx_reviews_user_id ON mentee_power.reviews(user_id);
CREATE INDEX IF NOT EXISTS idx_reviews_product_id ON mentee_power.reviews(product_id);
CREATE INDEX IF NOT EXISTS idx_promotion_usage_user_id ON mentee_power.promotion_usage(user_id);
CREATE INDEX IF NOT EXISTS idx_promotion_usage_promotion_id ON mentee_power.promotion_usage(promotion_id);
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON mentee_power.categories(parent_id);

--rollback DROP TABLE IF EXISTS mentee_power.promotion_usage, mentee_power.reviews, mentee_power.promotions, mentee_power.categories, mentee_power.inventory_locks, mentee_power.transfers, mentee_power.order_items, mentee_power.orders, mentee_power.products, mentee_power.users CASCADE;

