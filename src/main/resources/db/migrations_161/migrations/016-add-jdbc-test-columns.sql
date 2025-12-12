--liquibase formatted sql
--changeset mp173:add-jdbc-test-columns

-- Добавление столбцов для тестирования JDBC возможностей

-- Добавляем total_price в orders (если еще нет)
ALTER TABLE mentee_power.orders 
    ADD COLUMN IF NOT EXISTS total_price DECIMAL(10,2);

-- Заполняем total_price из total или total_amount, если он NULL
UPDATE mentee_power.orders 
SET total_price = COALESCE(total_amount, total) 
WHERE total_price IS NULL;

-- Добавляем order_date в orders (если еще нет)
ALTER TABLE mentee_power.orders 
    ADD COLUMN IF NOT EXISTS order_date TIMESTAMP;

-- Заполняем order_date из created_at, если он NULL
UPDATE mentee_power.orders 
SET order_date = created_at 
WHERE order_date IS NULL;

-- Устанавливаем значение по умолчанию для order_date
ALTER TABLE mentee_power.orders 
    ALTER COLUMN order_date SET DEFAULT CURRENT_TIMESTAMP;

-- Добавляем created_at в users (если еще нет)
ALTER TABLE mentee_power.users 
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

-- Заполняем created_at из registration_date, если он NULL
UPDATE mentee_power.users 
SET created_at = registration_date 
WHERE created_at IS NULL;

-- Устанавливаем значение по умолчанию для created_at
ALTER TABLE mentee_power.users 
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

--rollback ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS total_price; ALTER TABLE mentee_power.orders DROP COLUMN IF EXISTS order_date; ALTER TABLE mentee_power.users DROP COLUMN IF EXISTS created_at;

