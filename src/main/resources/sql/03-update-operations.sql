
UPDATE orders
SET status = 'shipped'
WHERE id = 1;

UPDATE users
SET
    name = 'Алексей Петрович Петров',
    updated_at = NOW()
WHERE email = 'alex.petrov@example.com'
RETURNING id, name, updated_at;

update products
set
    price = price + price*0.1;


update users
set status = 'premium'
where id in (
    select user_id
    from orders
    group by user_id
    having sum(total_price) > 50000
);

update products p
set stock_quantity = stock_quantity - oi.quantity
from order_items oi
where p.id = oi.product_id;


UPDATE products
SET status = CASE
                 WHEN stock_quantity = 0 THEN 'out_of_stock'
                 WHEN stock_quantity < 5 THEN 'low_stock'
                 ELSE 'in_stock'
    END;

