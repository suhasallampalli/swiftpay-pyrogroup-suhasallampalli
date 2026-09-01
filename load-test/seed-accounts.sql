-- Load-test seed data for the SwiftPay ledger database.
--
-- Creates 10,000 well-funded accounts (load-user-000001 .. load-user-010000)
-- so a 1,000,000-transaction run does not exhaust balances and hit the
-- "insufficient funds" path. This is NOT a Flyway/application migration --
-- run it manually against the ledger DB right before a load test:
--
--   docker exec -i swiftpay-postgres-ledger \
--     psql -U swiftpay -d swiftpay_ledger < load-test/seed-accounts.sql
--
-- Re-running is safe: existing rows are topped back up to the seed balance.

INSERT INTO accounts (user_id, balance, currency, version, created_at, updated_at)
SELECT
    'load-user-' || LPAD(g::text, 6, '0'),
    100000000.0000,          -- 100M units; ~20x more than the worst-case drain
    'USD',
    0,
    NOW(),
    NOW()
FROM generate_series(1, 10000) AS g
ON CONFLICT (user_id) DO UPDATE
    SET balance    = EXCLUDED.balance,
        updated_at = NOW();

SELECT COUNT(*) AS load_accounts,
       MIN(balance) AS min_balance,
       MAX(balance) AS max_balance
FROM accounts
WHERE user_id LIKE 'load-user-%';
