CREATE TABLE IF NOT EXISTS strategy_signal (
    id BIGSERIAL PRIMARY KEY,
    strategy_name TEXT NOT NULL,
    symbol TEXT NOT NULL,
    side TEXT NOT NULL,
    signal_strength NUMERIC(10, 4) NOT NULL DEFAULT 0,
    signal_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trade_order (
    id BIGSERIAL PRIMARY KEY,
    broker_order_id TEXT,
    symbol TEXT NOT NULL,
    side TEXT NOT NULL,
    order_type TEXT NOT NULL,
    price NUMERIC(18, 4),
    quantity INTEGER NOT NULL,
    status TEXT NOT NULL,
    strategy_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trade_fill (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES trade_order(id),
    fill_price NUMERIC(18, 4) NOT NULL,
    fill_quantity INTEGER NOT NULL,
    fill_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS position_snapshot (
    id BIGSERIAL PRIMARY KEY,
    symbol TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    avg_cost NUMERIC(18, 4) NOT NULL,
    snapshot_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS account_snapshot (
    id BIGSERIAL PRIMARY KEY,
    cash_balance NUMERIC(18, 4) NOT NULL,
    market_value NUMERIC(18, 4) NOT NULL,
    total_equity NUMERIC(18, 4) NOT NULL,
    snapshot_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS risk_check_log (
    id BIGSERIAL PRIMARY KEY,
    check_name TEXT NOT NULL,
    passed BOOLEAN NOT NULL,
    reason TEXT,
    context JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
