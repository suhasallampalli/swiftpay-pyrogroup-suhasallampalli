CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.payment_events (
    id               BIGSERIAL PRIMARY KEY,
    transaction_id   VARCHAR(255) NOT NULL UNIQUE,
    sender_id        VARCHAR(255) NOT NULL,
    receiver_id      VARCHAR(255) NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    event_type       VARCHAR(50) NOT NULL,
    event_timestamp  TIMESTAMPTZ NOT NULL,
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_analytics_event_type ON analytics.payment_events (event_type);
CREATE INDEX IF NOT EXISTS idx_analytics_event_timestamp ON analytics.payment_events (event_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_sender_id ON analytics.payment_events (sender_id);
