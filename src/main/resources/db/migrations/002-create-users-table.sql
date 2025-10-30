--liquibase formatted sql
--changeset developer-1:002-create-users-table
--comment: Создание таблицы пользователей

-- Создание основной таблицы
CREATE TABLE mentee_power.users (
                                    id BIGSERIAL PRIMARY KEY,
                                    name VARCHAR(100) NOT NULL,
                                    email VARCHAR(255) UNIQUE NOT NULL,
                                    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
                                    updated_at TIMESTAMP DEFAULT NOW() NOT NULL
);

-- Индексы для производительности
CREATE INDEX idx_users_email ON mentee_power.users(email);
CREATE INDEX idx_users_created_at ON mentee_power.users(created_at);

-- Комментарии к таблице
COMMENT ON TABLE mentee_power.users IS 'Пользователи системы - покупатели и администраторы';
COMMENT ON COLUMN mentee_power.users.email IS 'Email пользователя - используется для входа в систему';

--rollback DROP TABLE mentee_power.users;