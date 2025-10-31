--liquibase formatted sql
--changeset developer-2:004-add-user-phone
--comment: Добавление поля телефон к пользователям

-- Добавление колонки телефон
ALTER TABLE mentee_power.users ADD COLUMN phone VARCHAR(20);

-- Проверка формата телефона
ALTER TABLE mentee_power.users
    ADD CONSTRAINT check_phone_format
        CHECK (phone IS NULL OR phone ~ '^\\+?[1-9]\\d{1,14}$');

-- Индекс для поиска по телефону
CREATE INDEX idx_users_phone ON mentee_power.users(phone);

-- Комментарий
COMMENT ON COLUMN mentee_power.users.phone IS 'Номер телефона в международном формате';

--rollback DROP INDEX mentee_power.idx_users_phone; ALTER TABLE mentee_power.users DROP COLUMN phone;

--changeset developer-2:004-fix-phone-regex
--comment: Уточнение регулярки телефона для совместимости с настройками строк
-- Drop and recreate constraint with safe character classes
ALTER TABLE mentee_power.users DROP CONSTRAINT IF EXISTS check_phone_format;
ALTER TABLE mentee_power.users
    ADD CONSTRAINT check_phone_format
        CHECK (phone IS NULL OR phone ~ '^\+?[1-9][0-9]{1,14}$');

--rollback ALTER TABLE mentee_power.users DROP CONSTRAINT IF EXISTS check_phone_format;