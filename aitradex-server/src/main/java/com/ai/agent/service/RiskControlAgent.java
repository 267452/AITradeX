package com.ai.agent.service;

import com.ai.agent.model.MarketAnalysisResult;
import com.ai.agent.model.RiskReviewResult;
import com.ai.agent.model.TradingIntent;
import com.domain.request.ExecutionContext;
import com.domain.request.SignalRequest;
import com.domain.request.StrategyRunRequest;
import com.domain.response.RiskCheckResult;
import com.domain.response.StrategySignalResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.QuoteService;
import com.service.RiskService;
import com.service.TradeService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RiskControlAgent {
    private final RiskService riskService;
    private final TradeService tradeService;
    private final QuoteService quoteService;
    private final ObjectMapper objectMapper;

    public RiskControlAgent(RiskService riskService, TradeService tradeService,
                            QuoteService quoteService, ObjectMapper objectMapper) {
        this.riskService = riskService;
        this.tradeService = tradeService;
        this.quoteService = quoteService;
        this.objectMapper = objectMapper;
    }

    public RiskReviewResult review(TradingIntent intent, MarketAnalysisResult marketAnalysis,
                                   ExecutionContext executionContext) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("risk_rules", riskService.getEffectiveRiskConfigSnapshot());

        if (!intent.requiresRiskReview()) {
            return new RiskReviewResult(
                    "skipped",
                    false,
                    true,
                    "当前请求不涉及下单执行",
                    "无需进行交易风控复核",
                    null,
                    null,
                    details);
        }

        try {
            if (intent.isManualTrade()) {
                return reviewManualTrade(intent, marketAnalysis, executionContext, details);
            }
            if (intent.isStrategyRun()) {
                return reviewStrategyRun(intent, executionContext, details);
            }
            return new RiskReviewResult(
                    "completed",
                    true,
                    true,
                    "当前请求仅查看风控状态",
                    "已完成风控上下文审阅",
                    null,
                    null,
                    details);
        } catch (Exception e) {
            details.put("error", e.getMessage() == null ? "risk_review_failed" : e.getMessage());
            return new RiskReviewResult(
                    "failed",
                    true,
                    false,
                    "risk_review_failed",
                    "风控复核阶段出现异常",
                    null,
                    null,
                    details);
        }
    }

    private RiskReviewResult reviewManualTrade(TradingIntent intent, MarketAnalysisResult marketAnalysis,
                                               ExecutionContext executionContext, Map<String, Object> details) {
        if (intent.symbol() == null || intent.symbol().isBlank() || intent.side() == null || intent.side().isBlank()) {
            return new RiskReviewResult(
                    "blocked",
                    true,
                    false,
                    "manual_trade_missing_symbol_or_side",
                    "手动交易请求缺少明确的标的或方向",
                    null,
                    null,
                    details);
        }
        BigDecimal price = extractPrice(marketAnalysis);
        if (price == null && intent.symbol() != null) {
            Map<String, Object> quote = quoteService.getQuote(intent.symbol());
            price = extractPriceFromQuote(quote);
            details.put("quote", quote);
        }
        if (price == null) {
            return new RiskReviewResult(
                    "blocked",
                    true,
                    false,
                    "quote_missing",
                    "无法生成交易信号，缺少可用价格",
                    null,
                    null,
                    details);
        }

        int quantity = intent.quantity() == null || intent.quantity() <= 0 ? 100 : intent.quantity();
        String strategyName = intent.strategyName() == null || intent.strategyName().isBlank()
                ? "manual_command"
                : intent.strategyName();
        SignalRequest signal = tradeService.buildManualSignal(
                price,
                intent.side(),
                intent.symbol(),
                quantity,
                strategyName);

        RiskCheckResult preview = riskService.previewRisk(signal, executionContext);
        details.put("proposed_signal", toMap(signal));
        details.put("preview_result", Map.of("passed", preview.passed(), "reason", preview.reason()));

        return new RiskReviewResult(
                preview.passed() ? "ready" : "blocked",
                true,
                preview.passed(),
                preview.reason(),
                preview.passed() ? "可进入执行队列" : "建议先调整数量、方向或标的后再执行",
                signal,
                null,
                details);
    }

    private RiskReviewResult reviewStrategyRun(TradingIntent intent, ExecutionContext executionContext,
                                               Map<String, Object> details) {
        if (intent.symbol() == null || intent.symbol().isBlank()) {
            return new RiskReviewResult(
                    "blocked",
                    true,
                    false,
                    "strategy_run_missing_symbol",
                    "策略运行请求缺少标的代码",
                    null,
                    null,
                    details);
        }
        int quantity = intent.quantity() == null || intent.quantity() <= 0 ? 100 : intent.quantity();
        StrategyRunRequest request = new StrategyRunRequest(
                intent.strategyName() == null || intent.strategyName().isBlank() ? "sma_cmd_v1" : intent.strategyName(),
                intent.symbol(),
                "1d",
                5,
                20,
                quantity);
        details.put("strategy_request", toMap(request));

        StrategySignalResult generated = tradeService.generateSignalFromSma(request);
        details.put("strategy_result", Map.of("reason", generated.reason()));
        if (generated.signal() == null) {
            return new RiskReviewResult(
                    "blocked",
                    true,
                    false,
                    generated.reason(),
                    "策略当前没有形成可执行信号",
                    null,
                    request,
                    details);
        }

        RiskCheckResult preview = riskService.previewRisk(generated.signal(), executionContext);
        details.put("proposed_signal", toMap(generated.signal()));
        details.put("preview_result", Map.of("passed", preview.passed(), "reason", preview.reason()));

        return new RiskReviewResult(
                preview.passed() ? "ready" : "blocked",
                true,
                preview.passed(),
                preview.reason(),
                preview.passed() ? "策略信号可继续执行" : "策略信号已产生，但当前风控不允许直接执行",
                generated.signal(),
                request,
                details);
    }

    private BigDecimal extractPrice(MarketAnalysisResult marketAnalysis) {
        Object quote = marketAnalysis.facts().get("quote");
        if (quote instanceof Map<?, ?> quoteMap) {
            return extractPriceFromQuote(castMap(quoteMap));
        }
        return null;
    }

    private BigDecimal extractPriceFromQuote(Map<String, Object> quote) {
        Object raw = quote.get("price");
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }

    private Map<String, Object> castMap(Map<?, ?> value) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        value.forEach((k, v) -> normalized.put(String.valueOf(k), v));
        return normalized;
    }
}
