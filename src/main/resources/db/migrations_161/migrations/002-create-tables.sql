--liquibase formatted sql
--changeset mp161:create-tables
CREATE TABLE mentee_power.users (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(100) UNIQUE NOT NULL,
                       city VARCHAR(50),
                       registration_date TIMESTAMP DEFAULT NOW(),
                       is_active BOOLEAN DEFAULT true
);

CREATE TABLE mentee_power.categories (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            parent_id BIGINT REFERENCES mentee_power.categories(id)
);

CREATE TABLE mentee_power.products (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(200) NOT NULL,
                          description TEXT,
                          price DECIMAL(10,2) NOT NULL,
                          category_id BIGINT REFERENCES mentee_power.categories(id),
                          sku VARCHAR(50) UNIQUE,
                          created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE mentee_power.orders (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL REFERENCES mentee_power.users(id),
                        total DECIMAL(10,2) NOT NULL,
                        status VARCHAR(20) DEFAULT 'PENDING',
                        created_at TIMESTAMP DEFAULT NOW(),
                        region VARCHAR(50) DEFAULT 'MOSCOW'
);

CREATE TABLE mentee_power.order_items (
                             id BIGSERIAL PRIMARY KEY,
                             order_id BIGINT NOT NULL REFERENCES mentee_power.orders(id),
                             product_id BIGINT NOT NULL REFERENCES mentee_power.products(id),
                             quantity INTEGER NOT NULL CHECK (quantity > 0),
                             price DECIMAL(10,2) NOT NULL
);
--rollback DROP TABLE IF EXISTS mentee_power.order_items; DROP TABLE IF EXISTS mentee_power.orders; DROP TABLE IF EXISTS mentee_power.products; DROP TABLE IF EXISTS mentee_power.categories; DROP TABLE IF EXISTS mentee_power.users;