CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY,
                                     name VARCHAR(100) NOT NULL,
                                     email VARCHAR(100) UNIQUE NOT NULL,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
                                     id UUID PRIMARY KEY,
                                     user_id UUID NOT NULL REFERENCES users(id),
                                     total_price DECIMAL(10, 2) NOT NULL,
                                     status VARCHAR(50) NOT NULL,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DELETE FROM orders;
DELETE FROM users;

-- Тестовые данные users (с фиксированными UUID для стабильности тестов)
INSERT INTO users (id, name, email, created_at) VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'John Doe', 'john.doe@example.com', '2024-01-15 10:30:00'),
    ('550e8400-e29b-41d4-a716-446655440001', 'Jane Smith', 'jane.smith@example.com', '2024-01-16 14:20:00'),
    ('550e8400-e29b-41d4-a716-446655440002', 'Alice Johnson', 'alice.johnson@example.com', '2024-01-17 09:15:00'),
    ('550e8400-e29b-41d4-a716-446655440003', 'Bob Wilson', 'bob.wilson@example.com', '2024-01-18 16:45:00'),
    ('550e8400-e29b-41d4-a716-446655440004', 'Charlie Brown', 'charlie.brown@example.com', '2024-01-19 11:30:00');

-- Тестовые данные orders (ссылаемся на UUID пользователей)
INSERT INTO orders (id, user_id, total_price, status, created_at) VALUES
    ('550e8400-e29b-41d4-a716-446655440100', '550e8400-e29b-41d4-a716-446655440000', 15000.00, 'COMPLETED', '2024-01-01 10:00:00'),
    ('550e8400-e29b-41d4-a716-446655440101', '550e8400-e29b-41d4-a716-446655440000', 25000.00, 'COMPLETED', '2024-02-01 10:00:00'),
    ('550e8400-e29b-41d4-a716-446655440102', '550e8400-e29b-41d4-a716-446655440000', 30000.00, 'COMPLETED', '2024-03-01 10:00:00'),
    ('550e8400-e29b-41d4-a716-446655440103', '550e8400-e29b-41d4-a716-446655440001', 50000.00, 'COMPLETED', '2024-01-15 10:00:00'),
    ('550e8400-e29b-41d4-a716-446655440104', '550e8400-e29b-41d4-a716-446655440001', 10000.00, 'COMPLETED', '2024-02-15 10:00:00'),
    ('550e8400-e29b-41d4-a716-446655440105', '550e8400-e29b-41d4-a716-446655440002', 8000.00, 'COMPLETED', '2024-01-10 10:00:00'),
    ('550e8400-e29b-41d4-a716-446655440106', '550e8400-e29b-41d4-a716-446655440003', 60000.00, 'COMPLETED', '2024-01-20 10:00:00');