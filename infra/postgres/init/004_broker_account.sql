CREATE TABLE IF NOT EXISTS broker_account (
    id BIGSERIAL PRIMARY KEY,
    broker TEXT NOT NULL,
    account_name TEXT NOT NULL,
    base_url TEXT NOT NULL,
    api_key_encrypted TEXT,
    api_secret_encrypted TEXT,
    access_token_encrypted TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_broker_account_broker_created
    ON broker_account(broker, created_at DESC);
