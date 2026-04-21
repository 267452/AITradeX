CREATE TABLE IF NOT EXISTS flink_decision_signal (
    id BIGSERIAL PRIMARY KEY,
    decision_id TEXT NOT NULL UNIQUE,
    symbol TEXT NOT NULL,
    side TEXT NOT NULL,
    confidence NUMERIC(10, 4) NOT NULL DEFAULT 0,
    trigger_price NUMERIC(18, 8) NOT NULL DEFAULT 0,
    reference_price NUMERIC(18, 8) NOT NULL DEFAULT 0,
    price_change_bps INTEGER NOT NULL DEFAULT 0,
    window_seconds INTEGER NOT NULL DEFAULT 30,
    risk_gate_passed BOOLEAN NOT NULL DEFAULT TRUE,
    risk_reject_rate_5m NUMERIC(10, 4) NOT NULL DEFAULT 0,
    risk_context TEXT NOT NULL DEFAULT '',
    decision_reason TEXT NOT NULL DEFAULT '',
    source_event_time TIMESTAMPTZ,
    computed_at TIMESTAMPTZ NOT NULL,
    decision_latency_ms BIGINT NOT NULL DEFAULT 0,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_flink_decision_signal_computed
    ON flink_decision_signal(computed_at DESC);

CREATE INDEX IF NOT EXISTS idx_flink_decision_signal_symbol
    ON flink_decision_signal(symbol, computed_at DESC);

CREATE INDEX IF NOT EXISTS idx_flink_decision_signal_consumed
    ON flink_decision_signal(consumed, computed_at DESC);
