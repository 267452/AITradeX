package com.ai.agent.service;

import com.ai.agent.model.TradingIntent;
import com.ai.service.AiChatService;
import com.domain.request.TradeCommandRequest;
import com.domain.response.TradeCommandParseResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.TradeService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TradingIntentAgent {
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(
            "(?i)(\\d{6}(?:\\.(?:SH|SZ|BJ))?|[A-Z]{2,10}-[A-Z]{2,10}|[A-Z]{2,10}USDT|[A-Z][A-Z0-9]{0,9}(?:\\.US)?)");
    private static final List<String> ACCOUNT_KEYWORDS = List.of("账户", "资产", "持仓", "仓位", "订单", "总览");
    private static final List<String> RISK_KEYWORDS = List.of("风控", "风险", "限额", "仓位限制", "最大数量", "最大金额");
    private static final List<String> MARKET_KEYWORDS = List.of("行情", "报价", "价格", "k线", "走势", "分析", "看看", "研究");
    private static final String INTENT_SYSTEM_PROMPT = """
            你是交易系统的 intent-router agent，负责把用户请求归类成交易决策任务。

            你必须输出 JSON，不要输出 Markdown，不要解释。

            合法 category:
            - manual_trade
            - strategy_run
            - market_research
            - account_review
            - risk_review
            - general_advice

            输出格式:
            {
              "category": "manual_trade",
              "summary": "一句话描述意图",
              "symbol": "000001.SZ",
              "side": "buy|sell|",
              "quantity": 100,
              "strategy_name": "manual_command",
              "command_suggestion": "买入 000001 100",
              "executable": true,
              "requires_market_data": true,
              "requires_risk_review": true,
              "requires_portfolio_context": false
            }

            要求:
            - 如果是交易类请求，尽量提取 symbol / side / quantity。
            - 如果信息不完整但明显是交易类请求，也要给 command_suggestion。
            - 不确定时归类为 general_advice。
            """;

    private final AiChatService aiChatService;
    private final TradeService tradeService;
    private final ObjectMapper objectMapper;

    public TradingIntentAgent(AiChatService aiChatService, TradeService tradeService, ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.tradeService = tradeService;
        this.objectMapper = objectMapper;
    }

    public TradingIntent resolve(String message, String provider, String model,
                                 Map<String, Object> baseContext, boolean autoExecute) {
        TradeCommandParseResult parsed = tradeService.parseTradeCommand(new TradeCommandRequest(message, true));
        if (parsed.ok()) {
            return fromParsedCommand(parsed);
        }

        TradingIntent heuristic = resolveByHeuristics(message);
        if (heuristic != null) {
            return heuristic;
        }

        TradingIntent llmResolved = resolveByModel(message, provider, model, baseContext, autoExecute);
        if (llmResolved != null) {
            return llmResolved;
        }

        return new TradingIntent(
                "general_advice",
                "金融问题分析",
                null,
                null,
                null,
                null,
                "",
                false,
                false,
                false,
                false,
                "fallback",
                Map.of("message", message));
    }

    private TradingIntent fromParsedCommand(TradeCommandParseResult parsed) {
        if ("manual_signal".equals(parsed.action())) {
            return new TradingIntent(
                    "manual_trade",
                    "执行手动交易指令",
                    parsed.symbol(),
                    parsed.side(),
                    parsed.quantity(),
                    parsed.strategyName(),
                    buildTradeCommand(parsed.symbol(), parsed.side(), parsed.quantity()),
                    true,
                    true,
                    true,
                    true,
                    "rule",
                    Map.of("parser_action", parsed.action(), "parser_message", parsed.message()));
        }

        return new TradingIntent(
                "strategy_run",
                "运行策略并决定是否下单",
                parsed.symbol(),
                null,
                parsed.quantity(),
                parsed.strategyName(),
                buildStrategyCommand(parsed.symbol(), parsed.quantity()),
                true,
                true,
                true,
                true,
                "rule",
                Map.of("parser_action", parsed.action(), "parser_message", parsed.message()));
    }

    private TradingIntent resolveByHeuristics(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isBlank()) {
            return null;
        }

        String symbol = extractSymbol(normalized);
        String lowered = normalized.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, ACCOUNT_KEYWORDS)) {
            return new TradingIntent(
                    "account_review",
                    "查看账户、仓位与订单概览",
                    symbol,
                    null,
                    null,
                    null,
                    "",
                    false,
                    symbol != null,
                    false,
                    true,
                    "heuristic",
                    Map.of("matched_keywords", ACCOUNT_KEYWORDS));
        }

        if (containsAny(normalized, RISK_KEYWORDS)) {
            return new TradingIntent(
                    "risk_review",
                    "查看当前风控状态与执行约束",
                    symbol,
                    null,
                    null,
                    null,
                    "",
                    false,
                    symbol != null,
                    false,
                    true,
                    "heuristic",
                    Map.of("matched_keywords", RISK_KEYWORDS));
        }

        if (symbol != null && (containsAny(normalized, MARKET_KEYWORDS) || lowered.contains("what do you think"))) {
            return new TradingIntent(
                    "market_research",
                    "研究标的行情与趋势",
                    symbol,
                    null,
                    null,
                    null,
                    "",
                    false,
                    true,
                    false,
                    false,
                    "heuristic",
                    Map.of("matched_keywords", MARKET_KEYWORDS));
        }

        return null;
    }

    private TradingIntent resolveByModel(String message, String provider, String model,
                                         Map<String, Object> baseContext, boolean autoExecute) {
        try {
            Map<String, Object> promptPayload = new LinkedHashMap<>();
            promptPayload.put("message", message);
            promptPayload.put("auto_execute", autoExecute);
            promptPayload.put("broker_mode", baseContext.get("broker_mode"));
            promptPayload.put("risk_rules", baseContext.get("risk_rules"));

            String raw = aiChatService.generateText(toJson(promptPayload), provider, model, INTENT_SYSTEM_PROMPT);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String cleaned = raw.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("```\\s*$", "")
                    .trim();
            Map<String, Object> data = objectMapper.readValue(cleaned, new TypeReference<>() {});
            String category = sanitizeCategory(asString(data.get("category")));
            String symbol = normalizeSymbolCandidate(asString(data.get("symbol")));
            String side = sanitizeSide(asString(data.get("side")));
            Integer quantity = asInteger(data.get("quantity"));
            String strategyName = blankToNull(asString(data.get("strategy_name")));
            String commandSuggestion = blankToEmpty(asString(data.get("command_suggestion")));
            boolean executable = Boolean.TRUE.equals(data.get("executable"));
            boolean requiresMarketData = Boolean.TRUE.equals(data.get("requires_market_data"));
            boolean requiresRiskReview = Boolean.TRUE.equals(data.get("requires_risk_review"));
            boolean requiresPortfolioContext = Boolean.TRUE.equals(data.get("requires_portfolio_context"));
            String summary = blankToDefault(asString(data.get("summary")), "金融问题分析");

            if ("manual_trade".equals(category) && commandSuggestion.isBlank()) {
                commandSuggestion = buildTradeCommand(symbol, side, quantity);
            } else if ("strategy_run".equals(category) && commandSuggestion.isBlank()) {
                commandSuggestion = buildStrategyCommand(symbol, quantity);
            }

            return new TradingIntent(
                    category,
                    summary,
                    symbol,
                    side,
                    quantity,
                    strategyName,
                    commandSuggestion,
                    executable,
                    requiresMarketData,
                    requiresRiskReview,
                    requiresPortfolioContext,
                    "llm",
                    Map.of("raw", cleaned));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String extractSymbol(String message) {
        Matcher matcher = SYMBOL_PATTERN.matcher(message.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (candidate.matches("\\d{6}(?:\\.(?:SH|SZ|BJ))?") || candidate.endsWith(".US")) {
                return tradeService.normalizeSymbol(candidate);
            }
            return normalizeSymbolCandidate(candidate);
        }
        return null;
    }

    private String normalizeSymbolCandidate(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("\\d{6}(?:\\.(?:SH|SZ|BJ))?") || normalized.endsWith(".US")) {
            return tradeService.normalizeSymbol(normalized);
        }
        return normalized;
    }

    private String sanitizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "general_advice";
        }
        return switch (category) {
            case "manual_trade", "strategy_run", "market_research",
                    "account_review", "risk_review", "general_advice" -> category;
            default -> "general_advice";
        };
    }

    private String sanitizeSide(String side) {
        if (side == null || side.isBlank()) {
            return null;
        }
        String normalized = side.trim().toLowerCase(Locale.ROOT);
        if ("买入".equals(normalized)) {
            return "buy";
        }
        if ("卖出".equals(normalized)) {
            return "sell";
        }
        return switch (normalized) {
            case "buy", "sell" -> normalized;
            default -> null;
        };
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildTradeCommand(String symbol, String side, Integer quantity) {
        if (symbol == null || symbol.isBlank() || side == null || side.isBlank()) {
            return "";
        }
        int safeQty = quantity == null || quantity <= 0 ? 100 : quantity;
        return ("buy".equals(side) ? "买入 " : "卖出 ") + symbol + " " + safeQty;
    }

    private String buildStrategyCommand(String symbol, Integer quantity) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        int safeQty = quantity == null || quantity <= 0 ? 100 : quantity;
        return "运行策略 " + symbol + " " + safeQty;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
