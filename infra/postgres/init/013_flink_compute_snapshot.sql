CREATE TABLE IF NOT EXISTS flink_compute_snapshot (
    id BIGSERIAL PRIMARY KEY,
    source_events_1m INTEGER NOT NULL DEFAULT 0,
    source_events_5m INTEGER NOT NULL DEFAULT 0,
    processed_events_1m INTEGER NOT NULL DEFAULT 0,
    processed_events_5m INTEGER NOT NULL DEFAULT 0,
    order_fill_rate_5m NUMERIC(10, 4) NOT NULL DEFAULT 0,
    risk_reject_rate_5m NUMERIC(10, 4) NOT NULL DEFAULT 0,
    avg_workflow_latency_ms_5m BIGINT NOT NULL DEFAULT 0,
    p95_workflow_latency_ms_5m BIGINT NOT NULL DEFAULT 0,
    watermark_delay_ms BIGINT NOT NULL DEFAULT 0,
    queued_orders_now INTEGER NOT NULL DEFAULT 0,
    active_runs_now INTEGER NOT NULL DEFAULT 0,
    completed_runs_5m INTEGER NOT NULL DEFAULT 0,
    failed_runs_5m INTEGER NOT NULL DEFAULT 0,
    computed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_flink_compute_snapshot_computed
    ON flink_compute_snapshot(computed_at DESC);

CREATE TABLE IF NOT EXISTS flink_hot_symbol_snapshot (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES flink_compute_snapshot(id) ON DELETE CASCADE,
    symbol TEXT NOT NULL,
    order_count INTEGER NOT NULL DEFAULT 0,
    rank_no INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_flink_hot_symbol_snapshot_rank
    ON flink_hot_symbol_snapshot(snapshot_id, rank_no ASC, order_count DESC);
