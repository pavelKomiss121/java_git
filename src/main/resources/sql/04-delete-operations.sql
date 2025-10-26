
DELETE FROM orders
WHERE created_at < NOW() - INTERVAL '2 years'
RETURNING id, created_at;

delete from users
where status = 'inactive';

delete from products
where price = 0;

delete from orders
where id = 1;

UPDATE users
SET
    status = 'deleted',
    updated_at = NOW()
WHERE id = 999;

CREATE VIEW active_users AS
SELECT * FROM users
WHERE status != 'deleted';