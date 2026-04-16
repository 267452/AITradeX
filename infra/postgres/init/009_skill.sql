CREATE TABLE IF NOT EXISTS skill (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    icon TEXT NOT NULL DEFAULT '⚡',
    category TEXT NOT NULL DEFAULT 'general',
    status TEXT NOT NULL DEFAULT 'enabled',
    prompt_template TEXT NOT NULL DEFAULT '',
    variables JSONB NOT NULL DEFAULT '[]',
    tools JSONB NOT NULL DEFAULT '[]',
    enabled_tools TEXT NOT NULL DEFAULT '',
    run_count INTEGER NOT NULL DEFAULT 0,
    last_run_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_skill_category_status
    ON skill(category, status);

CREATE INDEX IF NOT EXISTS idx_skill_last_run
    ON skill(last_run_at DESC NULLS LAST, created_at DESC);

INSERT INTO skill (name, description, icon, category, status, prompt_template, variables, tools, enabled_tools, run_count, last_run_at)
VALUES
    ('基础问答', '简单的知识问答助手', '💬', 'assistant', 'enabled',
     '你是一个友好的助手，请根据上下文回答用户的问题。',
     '[]',
     '[]',
     '', 0, NULL),
    ('交易策略师', '专业的量化交易策略分析助手', '📈', 'trading', 'enabled',
     '你是一个专业的量化交易策略师。请根据市场数据和用户需求，提供交易建议。上下文：{{context}}',
     '["context", "risk_level"]',
     '["get_market_quote", "get_market_kline", "run_trade_command"]',
     'get_market_quote,get_market_kline,run_trade_command', 42, NOW() - INTERVAL '15 minutes'),
    ('风控审核员', '交易风险控制审核助手', '🛡️', 'risk', 'enabled',
     '你是一个严格的风控审核员。请评估以下交易的风险等级。账户信息：{{account_info}}',
     '["account_info", "transaction"]',
     '["get_risk_rules", "get_monitor_summary"]',
     'get_risk_rules,get_monitor_summary', 128, NOW() - INTERVAL '5 minutes'),
    ('市场分析师', '宏观经济与市场走势分析', '🔍', 'analysis', 'enabled',
     '你是一个资深的市场分析师。请分析以下市场数据并给出专业意见。',
     '[]',
     '["search_market_quote", "get_market_quote", "get_market_kline"]',
     'search_market_quote,get_market_quote,get_market_kline', 35, NOW() - INTERVAL '1 hour')
ON CONFLICT DO NOTHING;
