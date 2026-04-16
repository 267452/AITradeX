CREATE TABLE IF NOT EXISTS risk_rule (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    rule_type TEXT NOT NULL,
    rule_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT true,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_risk_rule_enabled ON risk_rule(enabled);
CREATE INDEX IF NOT EXISTS idx_risk_rule_type ON risk_rule(rule_type);

INSERT INTO risk_rule (name, rule_type, rule_config, enabled, priority) VALUES
('最大交易数量限制', 'max_quantity', '{"limit": 100000}', true, 10),
('最大交易金额限制', 'max_notional', '{"limit": 2000000}', true, 10),
('禁止做空', 'short_selling', '{"allow": false}', true, 15),
('交易频率限制', 'trade_frequency', '{"limit_seconds": 60}', true, 5),
('每日交易次数限制', 'daily_trade_limit', '{"limit": 10}', true, 5),
('价格波动限制', 'price_volatility', '{"threshold": 0.1}', true, 8),
('最大持仓限制', 'max_position', '{"limit_per_symbol": 50000}', true, 10),
('策略交易金额限制', 'strategy_notional', '{"limit": 10000000}', true, 12);
