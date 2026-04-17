CREATE TABLE IF NOT EXISTS workflow_run (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    workflow_id BIGINT REFERENCES workflow_definition(id) ON DELETE SET NULL,
    conversation_id BIGINT REFERENCES conversation_session(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'running',
    input_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT NOT NULL DEFAULT '',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflow_run_status_started
    ON workflow_run(status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_workflow_run_workflow
    ON workflow_run(workflow_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_workflow_run_conversation
    ON workflow_run(conversation_id, started_at DESC);

CREATE TABLE IF NOT EXISTS workflow_run_step (
    id BIGSERIAL PRIMARY KEY,
    workflow_run_id BIGINT NOT NULL REFERENCES workflow_run(id) ON DELETE CASCADE,
    step_order INTEGER NOT NULL,
    node_id TEXT,
    node_name TEXT,
    node_type TEXT NOT NULL DEFAULT 'task',
    status TEXT NOT NULL DEFAULT 'running',
    input_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT NOT NULL DEFAULT '',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflow_run_step_run_order
    ON workflow_run_step(workflow_run_id, step_order ASC);

ALTER TABLE strategy_signal
    ADD COLUMN IF NOT EXISTS run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS conversation_id BIGINT,
    ADD COLUMN IF NOT EXISTS workflow_id BIGINT,
    ADD COLUMN IF NOT EXISTS workflow_run_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_strategy_signal_run_id
    ON strategy_signal(run_id);

CREATE INDEX IF NOT EXISTS idx_strategy_signal_conversation
    ON strategy_signal(conversation_id, created_at DESC);

ALTER TABLE trade_order
    ADD COLUMN IF NOT EXISTS run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS conversation_id BIGINT,
    ADD COLUMN IF NOT EXISTS workflow_id BIGINT,
    ADD COLUMN IF NOT EXISTS workflow_run_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_trade_order_run_id
    ON trade_order(run_id);

CREATE INDEX IF NOT EXISTS idx_trade_order_conversation
    ON trade_order(conversation_id, created_at DESC);

ALTER TABLE risk_check_log
    ADD COLUMN IF NOT EXISTS run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS conversation_id BIGINT,
    ADD COLUMN IF NOT EXISTS workflow_id BIGINT,
    ADD COLUMN IF NOT EXISTS workflow_run_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_risk_check_log_run_id
    ON risk_check_log(run_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_risk_check_log_conversation
    ON risk_check_log(conversation_id, created_at DESC);
