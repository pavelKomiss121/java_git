CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     name VARCHAR(100) NOT NULL,
                                     email VARCHAR(100) UNIQUE NOT NULL,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
                                     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     user_id BIGINT NOT NULL,
                                     total_price DECIMAL(10, 2) NOT NULL,
                                     status VARCHAR(50) NOT NULL,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DELETE FROM users;
DELETE FROM orders;

-- Тестовые данные users
INSERT INTO users (name, email, created_at) VALUES
                                                ('John Doe', 'john.doe@example.com', '2024-01-15 10:30:00'),
                                                ('Jane Smith', 'jane.smith@example.com', '2024-01-16 14:20:00'),
                                                ('Alice Johnson', 'alice.johnson@example.com', '2024-01-17 09:15:00'),
                                                ('Bob Wilson', 'bob.wilson@example.com', '2024-01-18 16:45:00'),
                                                ('Charlie Brown', 'charlie.brown@example.com', '2024-01-19 11:30:00');

-- Тестовые данные orders
INSERT INTO orders (user_id, total_price, status, created_at) VALUES
                                                                   (1, 15000.00, 'COMPLETED', '2024-01-01 10:00:00'),
                                                                   (1, 25000.00, 'COMPLETED', '2024-02-01 10:00:00'),
                                                                   (1, 30000.00, 'COMPLETED', '2024-03-01 10:00:00'),
                                                                   (2, 50000.00, 'COMPLETED', '2024-01-15 10:00:00'),
                                                                   (2, 10000.00, 'COMPLETED', '2024-02-15 10:00:00'),
                                                                   (3, 8000.00, 'COMPLETED', '2024-01-10 10:00:00'),
                                                                   (4, 60000.00, 'COMPLETED', '2024-01-20 10:00:00');