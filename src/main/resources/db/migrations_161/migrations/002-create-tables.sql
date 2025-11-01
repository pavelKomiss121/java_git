--liquibase formatted sql
--changeset mp161:create-tables
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(100) UNIQUE NOT NULL,
                       city VARCHAR(50),
                       registration_date TIMESTAMP DEFAULT NOW(),
                       is_active BOOLEAN DEFAULT true
);

CREATE TABLE categories (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            parent_id BIGINT REFERENCES categories(id)
);

CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(200) NOT NULL,
                          description TEXT,
                          price DECIMAL(10,2) NOT NULL,
                          category_id BIGINT REFERENCES categories(id),
                          sku VARCHAR(50) UNIQUE,
                          created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL REFERENCES users(id),
                        total DECIMAL(10,2) NOT NULL,
                        status VARCHAR(20) DEFAULT 'PENDING',
                        created_at TIMESTAMP DEFAULT NOW(),
                        region VARCHAR(50) DEFAULT 'MOSCOW'
);

CREATE TABLE order_items (
                             id BIGSERIAL PRIMARY KEY,
                             order_id BIGINT NOT NULL REFERENCES orders(id),
                             product_id BIGINT NOT NULL REFERENCES products(id),
                             quantity INTEGER NOT NULL CHECK (quantity > 0),
                             price DECIMAL(10,2) NOT NULL
);
--rollback DROP TABLE order_items; DROP TABLE orders; DROP TABLE products; DROP TABLE categories; DROP TABLE users;