package com.ai.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record TradingIntent(
        String category,
        String summary,
        String symbol,
        String side,
        Integer quantity,
        String strategyName,
        String commandSuggestion,
        boolean executable,
        boolean requiresMarketData,
        boolean requiresRiskReview,
        boolean requiresPortfolioContext,
        String source,
        Map<String, Object> metadata) {

    public TradingIntent {
        metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }

    public boolean isTradeIntent() {
        return "manual_trade".equals(category) || "strategy_run".equals(category);
    }

    public boolean isManualTrade() {
        return "manual_trade".equals(category);
    }

    public boolean isStrategyRun() {
        return "strategy_run".equals(category);
    }

    public boolean isAccountReview() {
        return "account_review".equals(category);
    }

    public boolean isRiskReview() {
        return "risk_review".equals(category);
    }

    public boolean isMarketResearch() {
        return "market_research".equals(category);
    }
}
