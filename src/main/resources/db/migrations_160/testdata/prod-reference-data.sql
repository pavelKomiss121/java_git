--liquibase formatted sql
--changeset ops-team:prod-001-create-admin-user
--comment: Создание администратора системы

INSERT INTO mentee_power.users (name, email, phone) VALUES
    ('Системный администратор', 'admin@menteepower.com', '+7-800-555-0000');

--rollback DELETE FROM mentee_power.users WHERE email = 'admin@menteepower.com';