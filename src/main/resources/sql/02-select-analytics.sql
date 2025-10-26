
SELECT name, email, created_at
FROM users
WHERE created_at >= NOW() - INTERVAL '1 month'
ORDER BY created_at DESC;


select name, category, price
from products
where price in (
    select max(price)
    from products
    group by category
)


select u.name, sum(o.total_price) as total
from orders as o
         join users as u on user_id = u.id
group by u.id, u.name
order by total desc
limit 5


SELECT
    o.id as order_id,
    u.name as customer_name,
    o.total,
    o.status,
    o.created_at
FROM orders o
         JOIN users u ON o.user_id = u.id
ORDER BY o.created_at DESC;

SELECT
    o.id as order_id,
    u.name as customer_name,
    p.name as product_name,
    p.category as product_categiry,
    p.price as unit_price,
    o.quantity as quantity,
    o.total_price as order_total,
    o.status as order_status
FROM orders o
         JOIN users u ON o.user_id = u.id
         join products as p on o.product_id = p.id
ORDER BY o.id


select
    p.category,
    sum(o.total_price) as total_price,
    sum(o.quantity) as total_quantity,
    count(distinct o.id) as orders_count
from products as p
         join orders as o on p.id = o.product_id
group by category
order by total_price desc

select
    ROUND(avg(o.total_price), 2) as overall_average,
    u.name
from orders o
         join users as u on o.user_id = u.id
group by o.user_id, u.name
order by overall_average desc

SELECT
    p.id,
    p.name,
    p.price,
    p.category,
    p.stock_quantity
FROM products p
         LEFT JOIN order_items oi ON p.id = oi.product_id
WHERE oi.id IS NULL
ORDER BY p.category, p.name;


select
    u.name as user_name,
    count(o.id) as orders_count,
    sum(o.total_price) as total_spent,
    round(avg(o.total_price), 2) as average_value
from users u
         join orders o on u.id = o.user_id
group by u.id, u.name
having sum(o.total_price) > (
    select avg(user_total)
    from (
             select sum(total_price) as user_total
             from orders
             group by user_id
         ) user_averages
)
order by total_spent desc;

select
    category,
    name as product_name,
    total_sold
from (
         select
             p.category,
             p.name,
             sum(oi.quantity) as total_sold,
             row_number() over (partition by p.category order by sum(oi.quantity) desc) as rn
         from products p
                  join order_items oi on p.id = oi.product_id
         group by p.category, p.name
     ) ranked
where rn = 1
order by category;