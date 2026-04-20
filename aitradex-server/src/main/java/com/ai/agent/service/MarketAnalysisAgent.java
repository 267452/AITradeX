package com.ai.agent.service;

import com.ai.agent.model.MarketAnalysisResult;
import com.ai.agent.model.TradingIntent;
import com.service.QuoteService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarketAnalysisAgent {
    private final QuoteService quoteService;

    public MarketAnalysisAgent(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    public MarketAnalysisResult analyze(TradingIntent intent, Map<String, Object> baseContext) {
        Map<String, Object> facts = new LinkedHashMap<>();
        Map<String, Object> highlights = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        List<String> summaryParts = new ArrayList<>();

        facts.put("broker_mode", baseContext.get("broker_mode"));

        if (intent.requiresPortfolioContext()) {
            facts.put("monitor_summary", baseContext.get("monitor_summary"));
            facts.put("active_account", baseContext.get("active_account"));
            summaryParts.add("已装载账户、仓位和订单总览");
        }

        if (intent.isRiskReview()) {
            facts.put("risk_rules", baseContext.get("risk_rules"));
            summaryParts.add("已装载当前有效风控规则");
        }

        if ((intent.requiresMarketData() || intent.isTradeIntent()) && intent.symbol() != null && !intent.symbol().isBlank()) {
            try {
                Map<String, Object> quote = quoteService.getQuote(intent.symbol());
                facts.put("quote", quote);
                highlights.put("price", quote.get("price"));
                summaryParts.add("已获取 " + quote.get("symbol") + " 最新报价");
            } catch (Exception e) {
                warnings.add("quote_unavailable:" + e.getMessage());
            }

            try {
                Map<String, Object> kline = quoteService.getPublicKlines(intent.symbol(), "1d", 30);
                facts.put("kline", kline);
                MarketTrend trend = resolveTrend(kline);
                highlights.put("market_bias", trend.bias());
                highlights.put("trend_pct", trend.changePct());
                summaryParts.add("近 30 根日线呈现 " + trend.bias() + " 倾向");
            } catch (Exception e) {
                warnings.add("kline_unavailable:" + e.getMessage());
            }
        }

        if (!warnings.isEmpty()) {
            facts.put("warnings", warnings);
        }

        String summary = summaryParts.isEmpty()
                ? "已完成上下文整理，当前请求不依赖额外行情事实。"
                : String.join("；", summaryParts) + "。";
        String bias = String.valueOf(highlights.getOrDefault("market_bias", "neutral"));
        Double confidence = resolveConfidence(facts);

        return new MarketAnalysisResult(
                warnings.isEmpty() ? "completed" : "partial",
                summary,
                bias,
                confidence,
                facts,
                highlights);
    }

    private Double resolveConfidence(Map<String, Object> facts) {
        if (facts.containsKey("quote") && facts.containsKey("kline")) {
            return 0.82D;
        }
        if (facts.containsKey("quote")) {
            return 0.68D;
        }
        if (facts.containsKey("monitor_summary") || facts.containsKey("risk_rules")) {
            return 0.72D;
        }
        return 0.55D;
    }

    private MarketTrend resolveTrend(Map<String, Object> kline) {
        Object rawItems = kline.get("items");
        if (!(rawItems instanceof List<?> items) || items.size() < 2) {
            return new MarketTrend("neutral", BigDecimal.ZERO);
        }
        BigDecimal first = extractClose(items.get(0));
        BigDecimal last = extractClose(items.get(items.size() - 1));
        if (first == null || last == null || first.compareTo(BigDecimal.ZERO) == 0) {
            return new MarketTrend("neutral", BigDecimal.ZERO);
        }
        BigDecimal pct = last.subtract(first)
                .divide(first, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        String bias;
        if (pct.compareTo(BigDecimal.valueOf(2)) >= 0) {
            bias = "bullish";
        } else if (pct.compareTo(BigDecimal.valueOf(-2)) <= 0) {
            bias = "bearish";
        } else {
            bias = "neutral";
        }
        return new MarketTrend(bias, pct);
    }

    private BigDecimal extractClose(Object row) {
        if (!(row instanceof Map<?, ?> item)) {
            return null;
        }
        Object close = item.get("close");
        if (close instanceof BigDecimal decimal) {
            return decimal;
        }
        if (close == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(close));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record MarketTrend(String bias, BigDecimal changePct) {
    }
}
