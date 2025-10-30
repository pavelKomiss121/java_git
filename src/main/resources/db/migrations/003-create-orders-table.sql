--liquibase formatted sql
--changeset developer-1:003-create-orders-table
--comment: Создание таблицы заказов

-- Создание таблицы заказов
CREATE TABLE mentee_power.orders (
                                     id BIGSERIAL PRIMARY KEY,
                                     user_id BIGSERIAL NOT NULL REFERENCES mentee_power.users(id),
                                     total_price DECIMAL(10,2) NOT NULL,
                                     status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
                                     order_date TIMESTAMP DEFAULT NOW() NOT NULL,
                                     created_at TIMESTAMP DEFAULT NOW() NOT NULL
);

-- Индексы для производительности
CREATE INDEX idx_orders_user_id ON mentee_power.orders(user_id);
CREATE INDEX idx_orders_status ON mentee_power.orders(status);
CREATE INDEX idx_orders_order_date ON mentee_power.orders(order_date);

-- Check constraint для статуса
ALTER TABLE mentee_power.orders
    ADD CONSTRAINT check_order_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'));

-- Комментарии
COMMENT ON TABLE mentee_power.orders IS 'Заказы пользователей';
COMMENT ON COLUMN mentee_power.orders.status IS 'Статус заказа: PENDING, COMPLETED, CANCELLED';

--rollback DROP TABLE mentee_power.orders;