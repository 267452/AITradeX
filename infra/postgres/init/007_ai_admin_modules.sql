CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    vector_store TEXT NOT NULL DEFAULT 'Milvus',
    embedding_model TEXT NOT NULL DEFAULT 'bge-m3',
    status TEXT NOT NULL DEFAULT 'online',
    document_count INTEGER NOT NULL DEFAULT 0,
    slice_count INTEGER NOT NULL DEFAULT 0,
    last_sync_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    file_name TEXT NOT NULL,
    parse_status TEXT NOT NULL DEFAULT 'queued',
    chunk_count INTEGER NOT NULL DEFAULT 0,
    page_count INTEGER NOT NULL DEFAULT 0,
    sync_note TEXT NOT NULL DEFAULT '',
    source_path TEXT,
    last_sync_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'knowledge_document' AND column_name = 'source_path') THEN
        ALTER TABLE knowledge_document ADD COLUMN source_path TEXT;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS conversation_session (
    id BIGSERIAL PRIMARY KEY,
    session_code TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    channel TEXT NOT NULL DEFAULT 'Web',
    model_name TEXT NOT NULL,
    round_count INTEGER NOT NULL DEFAULT 0,
    user_rating NUMERIC(3, 1),
    tool_calls INTEGER NOT NULL DEFAULT 0,
    handoff_count INTEGER NOT NULL DEFAULT 0,
    knowledge_hit_rate NUMERIC(5, 2),
    status TEXT NOT NULL DEFAULT 'active',
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mcp_tool (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    transport_type TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'general',
    status TEXT NOT NULL DEFAULT 'enabled',
    last_test_at TIMESTAMPTZ,
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mcp_market (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    package_count INTEGER NOT NULL DEFAULT 0,
    visibility TEXT NOT NULL DEFAULT 'public',
    status TEXT NOT NULL DEFAULT 'online',
    refresh_note TEXT NOT NULL DEFAULT '',
    last_refresh_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workflow_definition (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    version_no INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'draft',
    run_count INTEGER NOT NULL DEFAULT 0,
    category TEXT NOT NULL DEFAULT 'general',
    last_run_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workflow_node_definition (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    node_name TEXT NOT NULL,
    node_type TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 1,
    config_note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_base_created
    ON knowledge_document(knowledge_base_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_conversation_session_last_message
    ON conversation_session(last_message_at DESC);

CREATE INDEX IF NOT EXISTS idx_workflow_definition_last_run
    ON workflow_definition(last_run_at DESC NULLS LAST, created_at DESC);
