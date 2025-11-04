-- src/main/resources/db/migrations/006-create-concurrency-test-schema.sql
--liquibase formatted sql
--changeset mp167:create-concurrency-test-schema

CREATE TABLE mentee_power.accounts (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL,
                          balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                          account_type VARCHAR(20) NOT NULL DEFAULT 'CHECKING',
                          created_at TIMESTAMP DEFAULT NOW(),
                          updated_at TIMESTAMP DEFAULT NOW(),

                          CONSTRAINT positive_balance CHECK (balance >= 0)
);

CREATE TABLE mentee_power.transactions (
                              id BIGSERIAL PRIMARY KEY,
                              account_id BIGINT NOT NULL REFERENCES accounts(id),
                              amount DECIMAL(15, 2) NOT NULL,
                              transaction_type VARCHAR(20) NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              created_at TIMESTAMP DEFAULT NOW(),
                              description TEXT,

                              CONSTRAINT valid_transaction_type CHECK (
                                  transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')
                                  ),
                              CONSTRAINT valid_status CHECK (
                                  status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')
                                  )
);

-- Индексы для производительности
CREATE INDEX idx_accounts_user_id ON mentee_power.accounts(user_id);
CREATE INDEX idx_accounts_balance ON mentee_power.accounts(balance);
CREATE INDEX idx_transactions_account_id ON mentee_power.transactions(account_id);
CREATE INDEX idx_transactions_created_at ON mentee_power.transactions(created_at);
CREATE INDEX idx_transactions_type_status ON mentee_power.transactions(transaction_type, status);

-- Тестовые данные для демонстрации
INSERT INTO mentee_power.accounts (user_id, balance, account_type) VALUES
                                                          (1, 10000.00, 'CHECKING'),
                                                          (2, 5000.00, 'SAVINGS'),
                                                          (3, 15000.00, 'CHECKING'),
                                                          (4, 2500.00, 'SAVINGS');

-- Тестовые транзакции
INSERT INTO mentee_power.transactions (account_id, amount, transaction_type, status, description) VALUES
                                                                                         (1, 1000.00, 'DEPOSIT', 'COMPLETED', 'Initial deposit'),
                                                                                         (2, 500.00, 'WITHDRAWAL', 'COMPLETED', 'ATM withdrawal'),
                                                                                         (3, 2000.00, 'TRANSFER', 'COMPLETED', 'Transfer from external account'),
                                                                                         (4, 300.00, 'DEPOSIT', 'COMPLETED', 'Interest payment');

--rollback DROP TABLE mentee_power.transactions; DROP TABLE mentee_power.accounts;