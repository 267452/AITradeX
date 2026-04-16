CREATE TABLE IF NOT EXISTS ai_config (
    id BIGSERIAL PRIMARY KEY,
    provider TEXT NOT NULL UNIQUE,
    model TEXT NOT NULL,
    model_id TEXT,
    api_key_encrypted TEXT,
    base_url TEXT,
    temperature DOUBLE PRECISION DEFAULT 0.3,
    max_tokens INTEGER DEFAULT 2000,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_config_provider ON ai_config(provider);
