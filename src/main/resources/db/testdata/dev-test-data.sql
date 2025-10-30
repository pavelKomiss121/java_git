--liquibase formatted sql
--changeset developer:dev-001-insert-test-users context:dev
--comment: Тестовые пользователи для разработки

INSERT INTO mentee_power.users (name, email, phone) VALUES
                                                        ('Алексей Петров', 'alex@menteepower.com', '+79001234567'),
                                                        ('Мария Сидорова', 'maria@menteepower.com', '+79002345678'),
                                                        ('Иван Козлов', 'ivan@menteepower.com', NULL);

--rollback DELETE FROM mentee_power.users WHERE email IN ('alex@menteepower.com', 'maria@menteepower.com', 'ivan@menteepower.com');

--changeset developer:dev-002-insert-test-products context:dev
--comment: Тестовые товары для разработки

INSERT INTO mentee_power.products (name, description, price, category) VALUES
                                                                           ('Ноутбук ASUS', 'Мощный игровой ноутбук', 85000.00, 'Компьютеры'),
                                                                           ('iPhone 15', 'Последняя модель iPhone', 120000.00, 'Телефоны'),
                                                                           ('Клавиатура механическая', 'RGB подсветка, Cherry MX', 8500.00, 'Аксессуары');

--rollback DELETE FROM mentee_power.products WHERE name IN ('Ноутбук ASUS', 'iPhone 15', 'Клавиатура механическая');