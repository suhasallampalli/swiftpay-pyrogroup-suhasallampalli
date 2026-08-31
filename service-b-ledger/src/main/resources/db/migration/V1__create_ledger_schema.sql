CREATE TABLE IF NOT EXISTS accounts (
    user_id    VARCHAR(255) PRIMARY KEY,
    balance    NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    currency   VARCHAR(3) NOT NULL DEFAULT 'USD',
    version    BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(255) PRIMARY KEY,
    sender_id      VARCHAR(255) NOT NULL,
    receiver_id    VARCHAR(255) NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    currency       VARCHAR(3) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_sender_id ON transactions (sender_id);
CREATE INDEX IF NOT EXISTS idx_transactions_receiver_id ON transactions (receiver_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions (status);

-- Seed test accounts with initial balances
INSERT INTO accounts (user_id, balance, currency) VALUES
    ('user-001', 10000.0000, 'USD'),
    ('user-002', 5000.0000, 'USD'),
    ('user-003', 2500.0000, 'USD'),
    ('user-poor', 10.0000, 'USD')
ON CONFLICT (user_id) DO NOTHING;
