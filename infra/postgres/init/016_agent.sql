CREATE TABLE IF NOT EXISTS agent_definition (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    icon TEXT NOT NULL DEFAULT '🤖',
    status TEXT NOT NULL DEFAULT 'enabled',
    model_name TEXT NOT NULL DEFAULT '',
    system_prompt TEXT NOT NULL DEFAULT '',
    temperature NUMERIC(3, 2) NOT NULL DEFAULT 0.70,
    max_tokens INTEGER NOT NULL DEFAULT 2048,
    tool_call_mode TEXT NOT NULL DEFAULT 'auto',
    run_count INTEGER NOT NULL DEFAULT 0,
    last_run_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agent_skill (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent_definition(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill_definition(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (agent_id, skill_id)
);

CREATE TABLE IF NOT EXISTS agent_mcp_tool (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent_definition(id) ON DELETE CASCADE,
    mcp_tool_id BIGINT NOT NULL REFERENCES mcp_tool(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (agent_id, mcp_tool_id)
);

CREATE TABLE IF NOT EXISTS agent_knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent_definition(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 1,
    top_k INTEGER NOT NULL DEFAULT 5,
    score_threshold NUMERIC(5, 4) NOT NULL DEFAULT 0.7000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (agent_id, knowledge_base_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_definition_status
    ON agent_definition(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_skill_agent
    ON agent_skill(agent_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_agent_mcp_tool_agent
    ON agent_mcp_tool(agent_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_agent_knowledge_base_agent
    ON agent_knowledge_base(agent_id, sort_order);

INSERT INTO agent_definition (name, description, icon, status, model_name, system_prompt, temperature, max_tokens, tool_call_mode, run_count, last_run_at)
VALUES
    ('智能投顾助手', '综合运用知识库、交易技能与行情工具的AI投顾', '🤖', 'enabled',
     'gpt-4.1', '你是一个专业的智能投顾助手，使用配置的知识库和工具为用户提供投资建议。',
     0.70, 2048, 'auto', 156, NOW() - INTERVAL '8 minutes'),
    ('策略研究助手', '专注于市场分析和策略研究的AI助手', '📊', 'enabled',
     'qwen-max', '你是一个专业的策略研究助手，擅长市场分析和策略制定。',
     0.60, 4096, 'auto', 87, NOW() - INTERVAL '30 minutes'),
    ('客服咨询助手', '处理用户咨询和常见问题的AI客服', '💬', 'enabled',
     'deepseek-chat', '你是一个友好的客服助手，请根据知识库内容回答用户问题。',
     0.50, 2048, 'auto', 342, NOW() - INTERVAL '2 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO agent_skill (agent_id, skill_id, sort_order)
SELECT ad.id, sd.id, 1
FROM agent_definition ad, skill_definition sd
WHERE ad.name = '智能投顾助手' AND sd.name IN ('交易策略师', '风控审核员', '市场分析师')
ON CONFLICT DO NOTHING;

INSERT INTO agent_skill (agent_id, skill_id, sort_order)
SELECT ad.id, sd.id, 1
FROM agent_definition ad, skill_definition sd
WHERE ad.name = '策略研究助手' AND sd.name IN ('市场分析师')
ON CONFLICT DO NOTHING;

INSERT INTO agent_skill (agent_id, skill_id, sort_order)
SELECT ad.id, sd.id, 1
FROM agent_definition ad, skill_definition sd
WHERE ad.name = '客服咨询助手' AND sd.name IN ('基础问答')
ON CONFLICT DO NOTHING;

INSERT INTO agent_mcp_tool (agent_id, mcp_tool_id, sort_order)
SELECT ad.id, mt.id, 1
FROM agent_definition ad, mcp_tool mt
WHERE ad.name = '智能投顾助手' AND mt.name IN ('Quote Search', 'Risk Checker')
ON CONFLICT DO NOTHING;

INSERT INTO agent_mcp_tool (agent_id, mcp_tool_id, sort_order)
SELECT ad.id, mt.id, 1
FROM agent_definition ad, mcp_tool mt
WHERE ad.name = '策略研究助手' AND mt.name IN ('Quote Search', 'News Fetcher')
ON CONFLICT DO NOTHING;

INSERT INTO agent_knowledge_base (agent_id, knowledge_base_id, sort_order, top_k, score_threshold)
SELECT ad.id, kb.id, 1, 5, 0.70
FROM agent_definition ad, knowledge_base kb
WHERE ad.name = '智能投顾助手' AND kb.name IN ('投研知识库', '交易规则库')
ON CONFLICT DO NOTHING;

INSERT INTO agent_knowledge_base (agent_id, knowledge_base_id, sort_order, top_k, score_threshold)
SELECT ad.id, kb.id, 1, 5, 0.70
FROM agent_definition ad, knowledge_base kb
WHERE ad.name = '策略研究助手' AND kb.name IN ('投研知识库')
ON CONFLICT DO NOTHING;

INSERT INTO agent_knowledge_base (agent_id, knowledge_base_id, sort_order, top_k, score_threshold)
SELECT ad.id, kb.id, 1, 10, 0.65
FROM agent_definition ad, knowledge_base kb
WHERE ad.name = '客服咨询助手' AND kb.name IN ('产品文档库', '客服知识库')
ON CONFLICT DO NOTHING;
