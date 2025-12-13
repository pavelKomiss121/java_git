-- src/main/resources/db/migrations/017-create-transaction-test-schema.sql
--liquibase formatted sql
--changeset mp-transaction:create-transaction-test-schema

CREATE TABLE IF NOT EXISTS public.accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--rollback DROP TABLE IF EXISTS public.accounts;

