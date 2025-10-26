CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     name VARCHAR(100) NOT NULL,
                                     email VARCHAR(100) UNIQUE NOT NULL,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DELETE FROM users;

-- Тестовые данные
INSERT INTO users (name, email, created_at) VALUES
                                                ('John Doe', 'john.doe@example.com', '2024-01-15 10:30:00'),
                                                ('Jane Smith', 'jane.smith@example.com', '2024-01-16 14:20:00'),
                                                ('Alice Johnson', 'alice.johnson@example.com', '2024-01-17 09:15:00'),
                                                ('Bob Wilson', 'bob.wilson@example.com', '2024-01-18 16:45:00'),
                                                ('Charlie Brown', 'charlie.brown@example.com', '2024-01-19 11:30:00');