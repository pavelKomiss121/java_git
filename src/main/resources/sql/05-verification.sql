
SELECT
    'users' as table_name,
    COUNT(*) as record_count
FROM users
UNION ALL
SELECT 'products', COUNT(*) FROM products
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'order_items', COUNT(*) FROM order_items;

select order_id
from order_items
where order_id not in (select id from orders);

select product_id
from order_items
where product_id not in (select id from products);

select user_id
from orders
where user_id not in (select id from users);

select id, name, price
from products
where price < 0;


select id, name, stock_quantity
from products
where stock_quantity < 0;

select email, count(*) as cnt
from users
group by email
having count(*) > 1;