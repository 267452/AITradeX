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
    last_sync_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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

INSERT INTO knowledge_base (name, description, vector_store, embedding_model, status, document_count, slice_count, last_sync_at)
VALUES
    ('投研知识库', '股票策略、宏观解读与行业跟踪资料', 'Milvus', 'bge-m3', 'online', 42, 1420, NOW() - INTERVAL '12 minutes'),
    ('交易规则库', '风控阈值、下单规则与经纪商约束', 'Weaviate', 'bge-m3', 'syncing', 28, 860, NOW() - INTERVAL '25 minutes'),
    ('产品文档库', '接口说明、运行手册与常见问题', 'Qdrant', 'bge-large-zh', 'online', 56, 2112, NOW() - INTERVAL '8 minutes'),
    ('客服知识库', '用户问答、业务流程与话术模板', 'Milvus', 'text-embedding-3-large', 'draft', 22, 500, NOW() - INTERVAL '2 hours')
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_document (knowledge_base_id, file_name, parse_status, chunk_count, page_count, sync_note, last_sync_at)
SELECT kb.id, doc.file_name, doc.parse_status, doc.chunk_count, doc.page_count, doc.sync_note, doc.last_sync_at
FROM (VALUES
    ('投研知识库', '2026Q1-策略复盘.pdf', 'indexed', 196, 42, '已解析完成并写入向量库', NOW() - INTERVAL '12 minutes'),
    ('交易规则库', '券商接入说明.md', 'pending', 34, 0, '待二次切片并补规则标签', NOW() - INTERVAL '35 minutes'),
    ('产品文档库', '交易风控手册.docx', 'queued', 87, 16, 'OCR 完成，等待写入向量库', NOW() - INTERVAL '1 hour')
) AS doc(base_name, file_name, parse_status, chunk_count, page_count, sync_note, last_sync_at)
JOIN knowledge_base kb ON kb.name = doc.base_name
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_document kd WHERE kd.file_name = doc.file_name
);

INSERT INTO conversation_session (session_code, title, channel, model_name, round_count, user_rating, tool_calls, handoff_count, knowledge_hit_rate, status, last_message_at)
VALUES
    ('Session-22031', '投顾助手', 'Web', 'gpt-4.1', 12, 4.8, 3, 0, 91.50, 'active', NOW() - INTERVAL '5 minutes'),
    ('Session-22018', '开户客服', 'Web', 'deepseek-chat', 7, 4.3, 1, 1, 82.00, 'follow_up', NOW() - INTERVAL '18 minutes'),
    ('Session-21994', '策略分析', 'Admin', 'qwen-max', 18, 4.9, 2, 0, 88.00, 'stable', NOW() - INTERVAL '28 minutes'),
    ('Session-21960', '内部运营', 'Admin', 'o4-mini', 5, 4.6, 0, 0, 92.00, 'archived', NOW() - INTERVAL '2 hours')
ON CONFLICT (session_code) DO NOTHING;

INSERT INTO mcp_tool (name, transport_type, endpoint, category, status, last_test_at, note)
VALUES
    ('Quote Search', 'REMOTE_HTTP', 'https://mcp.example.com/quote', 'quote', 'enabled', NOW() - INTERVAL '3 minutes', '行情查询工具'),
    ('News Fetcher', 'REMOTE_SSE', 'https://mcp.example.com/news/sse', 'news', 'warning', NOW() - INTERVAL '20 minutes', '资讯流工具'),
    ('Doc Parser', 'LOCAL_STDIO', 'python tools/doc_parser.py', 'document', 'enabled', NOW() - INTERVAL '10 minutes', '文档解析工具'),
    ('Risk Checker', 'LOCAL_STDIO', 'python tools/risk_checker.py', 'risk', 'gray', NOW() - INTERVAL '45 minutes', '风控校验工具')
ON CONFLICT DO NOTHING;

INSERT INTO mcp_market (name, package_count, visibility, status, refresh_note, last_refresh_at)
VALUES
    ('官方市场', 24, 'public', 'online', '版本策略稳定', NOW() - INTERVAL '1 hour'),
    ('团队私有市场', 8, 'private', 'online', '需要签名校验', NOW() - INTERVAL '2 hours'),
    ('实验市场', 5, 'beta', 'warning', '用于测试工作流节点与多模态工具', NOW() - INTERVAL '4 hours')
ON CONFLICT DO NOTHING;

INSERT INTO workflow_definition (name, description, version_no, status, run_count, category, last_run_at)
VALUES
    ('研报问答流程', '知识检索 -> 模型回答 -> 引用补全 -> 结果审核', 3, 'published', 38, 'qa', NOW() - INTERVAL '10 minutes'),
    ('开户线索分发', '表单接入 -> 意图识别 -> CRM 入库 -> 人工分配', 1, 'draft', 12, 'crm', NOW() - INTERVAL '3 hours'),
    ('高风险交易提醒', '信号监听 -> 风险校验 -> 通知节点 -> 人工确认', 2, 'critical', 87, 'risk', NOW() - INTERVAL '6 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO workflow_node_definition (workflow_id, node_name, node_type, sort_order, config_note)
SELECT wf.id, node.node_name, node.node_type, node.sort_order, node.config_note
FROM (VALUES
    ('研报问答流程', '开始节点', 'start', 1, '接收入参、会话上下文和用户属性'),
    ('研报问答流程', '知识检索', 'knowledge_retrieval', 2, '按知识库、topK、score 阈值召回'),
    ('研报问答流程', '模型生成', 'llm', 3, '选择供应商、模型与提示词模板'),
    ('研报问答流程', '人工审核', 'human_review', 4, '高风险内容转人工确认'),
    ('高风险交易提醒', '开始节点', 'start', 1, '接收交易信号与账户上下文'),
    ('高风险交易提醒', 'MCP 工具', 'mcp_tool', 2, '调用行情与新闻扩展工具'),
    ('高风险交易提醒', '结束节点', 'end', 3, '输出审核结果与执行日志')
) AS node(workflow_name, node_name, node_type, sort_order, config_note)
JOIN workflow_definition wf ON wf.name = node.workflow_name
WHERE NOT EXISTS (
    SELECT 1
    FROM workflow_node_definition wnd
    WHERE wnd.workflow_id = wf.id AND wnd.node_name = node.node_name
);
