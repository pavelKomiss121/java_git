
-- Схема БД для тестов (упрощенная версия)

CREATE SCHEMA IF NOT EXISTS mentee_power;
SET search_path TO mentee_power;

-- Таблица пользователей
CREATE TABLE users (
                       id uuid PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       created_at TIMESTAMP DEFAULT NOW()
);

-- Таблица заказов
CREATE TABLE orders (
                        id uuid PRIMARY KEY,
                        user_id uuid NOT NULL,
                        total_price DECIMAL(10,2) NOT NULL,
                        status VARCHAR(20) DEFAULT 'PENDING',
                        order_date TIMESTAMP DEFAULT NOW(),
                        created_at TIMESTAMP DEFAULT NOW(),
                        CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)

);

-- Таблица товаров
CREATE TABLE products (
                          id uuid PRIMARY KEY,
                          name VARCHAR(200) NOT NULL,
                          price DECIMAL(10,2) NOT NULL,
                          category VARCHAR(100),
                          stock_quantity INTEGER DEFAULT 0
);

-- Таблица позиций заказа
CREATE TABLE order_items (
                             id uuid PRIMARY KEY,
                             order_id uuid NOT NULL,
                             product_id uuid NOT NULL,
                             quantity INTEGER NOT NULL,
                             unit_price DECIMAL(10,2) NOT NULL,
                             CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
                             CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Индексы для производительности
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);