CREATE TABLE IF NOT EXISTS market_bar (
    id BIGSERIAL PRIMARY KEY,
    symbol TEXT NOT NULL,
    timeframe TEXT NOT NULL,
    bar_time TIMESTAMPTZ NOT NULL,
    open NUMERIC(18, 6) NOT NULL,
    high NUMERIC(18, 6) NOT NULL,
    low NUMERIC(18, 6) NOT NULL,
    close NUMERIC(18, 6) NOT NULL,
    volume NUMERIC(20, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(symbol, timeframe, bar_time)
);

CREATE INDEX IF NOT EXISTS idx_market_bar_symbol_tf_time
    ON market_bar(symbol, timeframe, bar_time DESC);
