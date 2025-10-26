
INSERT INTO users (name, email) VALUES
                                    ('Алексей Петров', 'alex.petrov@example.com'),
                                    ('Мария Сидорова', 'maria.sidorova@example.com'),
                                    ('Александр Петров', 'alexander.petrov@example.com'),
                                    ('Елена Козлова', 'elena.kozlova@example.com'),
                                    ('Дмитрий Волков', 'dmitry.volkov@example.com'),
                                    ('Анна Морозова', 'anna.morozova@example.com'),
                                    ('Сергей Лебедев', 'sergey.lebedev@example.com'),
                                    ('Ольга Новикова', 'olga.novikova@example.com'),
                                    ('Игорь Соколов', 'igor.sokolov@example.com'),
                                    ('Татьяна Кузнецова', 'tatyana.kuznetsova@example.com');



INSERT INTO products (name, price, category, stock_quantity) VALUES
                                 ('Ноутбук ASUS ROG', 89999.00, 'electronics', 5),
                                 ('Мышка Logitech MX Master', 7500.00, 'electronics', 20),
                                 ('Книга "Чистый код"', 2500.00, 'books', 100),
                                 ('iPhone 15 Pro', 120000.00, 'electronics', 8),
                                 ('Наушники Sony WH-1000XM4', 25000.00, 'electronics', 15),
                                 ('Клавиатура Razer BlackWidow', 12000.00, 'electronics', 25),
                                 ('Книга "Архитектура ПО"', 3200.00, 'books', 80),
                                 ('Футболка Nike Dri-FIT', 3500.00, 'clothing', 50),
                                 ('Джинсы Levis 501', 8000.00, 'clothing', 30),
                                 ('Кроссовки Adidas Ultraboost', 15000.00, 'shoes', 20),
                                 ('Кофе Lavazza Qualita Oro', 1200.00, 'food', 200),
                                 ('Чай Earl Grey Twinings', 800.00, 'food', 150),
                                 ('Монитор Samsung 27" 4K', 45000.00, 'electronics', 12),
                                 ('Книга "Design Patterns"', 2800.00, 'books', 60),
                                 ('Куртка The North Face', 18000.00, 'clothing', 18);


insert into orders (user_id, product_id, quantity, unit_price, status) values
                                                                           (1, 1, 1, 89999.00, 'COMPLETED'),
                                                                           (1, 2, 2, 2499.00,  'COMPLETED'),
                                                                           (2, 3, 1, 3500.00,  'PENDING'),
                                                                           (3, 1, 1, 89999.00,  'SHIPPED'),
                                                                           (4, 4, 1, 8999.00,  'COMPLETED'),
                                                                           (5, 5, 1, 4200.00,  'COMPLETED'),
                                                                           (2, 6, 1, 15999.00, 'PENDING'),
                                                                           (3, 8, 2, 2800.00, 'COMPLETED'),
                                                                           (4, 7, 1, 25000.00, 'SHIPPED'),
                                                                           (5, 2, 1, 2499.00, 'COMPLETED');

-- TODO: Добавить товары в заказы (order_items)
INSERT INTO order_items (order_id, product_id, quantity, price) VALUES
                            (1, 1, 1, 89999.00),
                            (1, 2, 1, 7500.00),

                            (2, 10, 1, 15000.00),

                            (3, 13, 1, 45000.00),

                            (4, 9, 1, 8000.00),
                            (4, 8, 1, 3500.00),

                            (5, 4, 1, 120000.00);