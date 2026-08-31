CREATE TABLE IF NOT EXISTS payments (
    transaction_id VARCHAR(255) PRIMARY KEY,
    sender_id      VARCHAR(255) NOT NULL,
    receiver_id    VARCHAR(255) NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    currency       VARCHAR(3) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_sender_id ON payments (sender_id);
CREATE INDEX IF NOT EXISTS idx_payments_receiver_id ON payments (receiver_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);
