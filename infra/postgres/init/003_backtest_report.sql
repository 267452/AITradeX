CREATE TABLE IF NOT EXISTS backtest_report (
    id BIGSERIAL PRIMARY KEY,
    strategy_name TEXT NOT NULL,
    symbol TEXT NOT NULL,
    timeframe TEXT NOT NULL,
    short_window INTEGER NOT NULL,
    long_window INTEGER NOT NULL,
    initial_cash NUMERIC(18, 4) NOT NULL,
    trades INTEGER NOT NULL,
    win_rate NUMERIC(10, 4) NOT NULL,
    total_return NUMERIC(10, 4) NOT NULL,
    max_drawdown NUMERIC(10, 4) NOT NULL,
    final_equity NUMERIC(18, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_backtest_report_symbol_created
    ON backtest_report(symbol, created_at DESC);
