package com.ai.agent.service;

import com.ai.agent.model.ExecutionResult;
import com.ai.agent.model.MarketAnalysisResult;
import com.ai.agent.model.RiskReviewResult;
import com.ai.agent.model.TradingIntent;
import com.ai.service.AiChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TradingResponseAgent {
    private static final String RESPONSE_SYSTEM_PROMPT = """
            你是交易系统的 summary agent，需要把多 agent 的结构化结论翻译给用户。

            要求：
            1. 只基于输入事实总结，不要编造价格、仓位和执行结果。
            2. 明确区分“分析建议”和“已执行”。
            3. 不承诺收益，不煽动交易。
            4. 输出纯文本中文，2-5 句话，不要 Markdown 标题。
            """;

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public TradingResponseAgent(AiChatService aiChatService, ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.objectMapper = objectMapper;
    }

    public String compose(String userMessage, String provider, String model, boolean autoExecute,
                          TradingIntent intent, MarketAnalysisResult marketAnalysis,
                          RiskReviewResult riskReview, ExecutionResult executionResult,
                          Map<String, Object> baseContext) {
        String aiSummary = composeWithModel(userMessage, provider, model, autoExecute, intent,
                marketAnalysis, riskReview, executionResult, baseContext);
        if (aiSummary != null && !aiSummary.isBlank()) {
            return aiSummary.trim();
        }
        return composeFallback(autoExecute, intent, marketAnalysis, riskReview, executionResult, baseContext);
    }

    private String composeWithModel(String userMessage, String provider, String model, boolean autoExecute,
                                    TradingIntent intent, MarketAnalysisResult marketAnalysis,
                                    RiskReviewResult riskReview, ExecutionResult executionResult,
                                    Map<String, Object> baseContext) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("user_message", userMessage);
            payload.put("mode", autoExecute ? "execute" : "analysis");
            payload.put("intent", intent);
            payload.put("market_analysis", marketAnalysis);
            payload.put("risk_review", riskReview);
            payload.put("execution_result", executionResult);
            payload.put("broker_mode", baseContext.get("broker_mode"));

            String out = aiChatService.generateText(toJson(payload), provider, model, RESPONSE_SYSTEM_PROMPT);
            return out == null || out.isBlank() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    private String composeFallback(boolean autoExecute, TradingIntent intent, MarketAnalysisResult marketAnalysis,
                                   RiskReviewResult riskReview, ExecutionResult executionResult,
                                   Map<String, Object> baseContext) {
        if (intent.isAccountReview()) {
            Map<String, Object> summary = toMap(baseContext.get("monitor_summary"));
            return String.format(
                    "当前账户总览已整理完成，累计订单 %s 笔，已成交 %s 笔，排队中 %s 笔。当前总权益约 %s，现金约 %s，后续可以继续按标的或策略下钻分析。",
                    summary.getOrDefault("orders_total", 0),
                    summary.getOrDefault("orders_filled", 0),
                    summary.getOrDefault("orders_queued", 0),
                    formatNumber(summary.get("latest_equity")),
                    formatNumber(summary.get("latest_cash")));
        }

        if (intent.isRiskReview() && !riskReview.applicable()) {
            return "当前风控规则和执行约束已经整理完成，这次请求本身不涉及直接下单，所以系统没有进入执行阶段。后续如果你给出标的和数量，我可以先走一遍预检。";
        }

        if (executionResult.executed()) {
            return String.format(
                    "这次请求已经按 agent 决策链完成执行，市场分析结论是 %s，风控预检结果为 %s。当前信号已经进入订单流程，建议继续关注成交回报和仓位变化。",
                    marketAnalysis.marketBias(),
                    riskReview.reason());
        }

        if ("blocked".equals(executionResult.status())) {
            return String.format(
                    "系统已经完成多 agent 评估，但当前不建议直接执行。主要原因是 %s；市场侧结论偏 %s，如果你愿意，我们可以先调整数量、方向或策略参数再复核一次。",
                    riskReview.reason(),
                    marketAnalysis.marketBias());
        }

        if ("analysis_only".equals(executionResult.status())) {
            String quoteText = resolveQuoteText(marketAnalysis);
            return String.format(
                    "这次先走的是分析模式，没有直接发单。%s 风控预检结果为 %s；如果你决定执行，可以直接使用指令“%s”。",
                    quoteText,
                    riskReview.reason(),
                    executionResult.commandSuggestion());
        }

        if (intent.isMarketResearch()) {
            return String.format(
                    "标的的行情上下文已经整理完，当前市场倾向是 %s，置信度约 %.0f%%。如果你准备进一步下单，下一步最好补一轮风控预检和仓位评估。",
                    marketAnalysis.marketBias(),
                    marketAnalysis.confidence() * 100);
        }

        return "我已经把这次请求转成 agent 化决策链处理完了，当前结论更偏研究和建议，还没有进入真实执行。后面最值得补的是更细的组合约束、审批流和复盘闭环。";
    }

    private String resolveQuoteText(MarketAnalysisResult marketAnalysis) {
        Object quoteObj = marketAnalysis.facts().get("quote");
        Map<String, Object> quote = toMap(quoteObj);
        if (quote.isEmpty()) {
            return "当前没有拿到稳定的实时价格";
        }
        return String.format("已拿到 %s 的最新价格 %s",
                quote.getOrDefault("symbol", "-"),
                formatNumber(quote.get("price")));
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private String formatNumber(Object value) {
        if (value == null) {
            return "-";
        }
        try {
            BigDecimal decimal = new BigDecimal(String.valueOf(value));
            return decimal.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
