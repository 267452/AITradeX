package com.ai.service;

import com.config.AppProperties;
import com.domain.request.SignalRequest;
import com.domain.request.StrategyRunRequest;
import com.domain.request.TradeCommandRequest;
import com.domain.response.SignalProcessResponse;
import com.domain.response.StrategySignalResult;
import com.domain.response.TradeCommandParseResult;
import com.repository.MonitorRepository;
import com.service.BrokerAccountService;
import com.service.BrokerService;
import com.service.OkxService;
import com.service.QuoteService;
import com.service.TradeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FinancialAgentService {
    
    private static final String AGENT_SYSTEM_PROMPT = """
            你是 AIBuy 金融业务智能体。你的职责不是闲聊，而是围绕交易、行情、风控、账户状态来帮助用户完成任务。

            工作原则：
            1. 先理解用户意图，再决定是否需要调用工具。
            2. 优先使用工具获取事实，不要臆测价格、仓位、风控状态。
            3. 对涉及真实下单、策略执行、切换通道等高风险动作，要明确提示风险。
            4. 如果当前模式不允许执行，给出建议指令或下一步建议。
            5. 输出必须是 JSON，不要附带 Markdown 代码块。

            你可以使用的工具：
            - get_broker_mode: 查看当前券商模式
            - get_risk_rules: 查看风险规则
            - get_monitor_summary: 查看账户与订单总览
            - get_active_account: 查看当前激活账户
            - search_market_quote: 搜索标的
            - get_market_quote: 查询单个标的行情
            - get_market_kline: 查询 K 线
            - get_okx_portfolio: 查看 OKX 持仓
            - run_trade_command: 执行或解析交易指令
            - run_strategy: 执行策略

            你的 JSON 输出格式必须严格为：
            {
              "intent": "一句话概括用户意图",
              "task_state": "created|researching|analyzing|ready_to_execute|executing|completed|blocked",
              "response": "给用户看的自然语言回复",
              "risk_level": "low|medium|high",
              "done": false,
              "next_step": "下一步要做什么",
              "observation_summary": "本轮观察结论",
              "requires_confirmation": true,
              "command_suggestion": "可选，标准交易指令字符串，没有就填空字符串",
              "tool_calls": [
                {
                  "name": "工具名",
                  "arguments": {
                    "key": "value"
                  }
                }
              ]
            }

            规则补充：
            - 如果已经有足够信息直接回答，tool_calls 返回空数组，done=true。
            - 每轮最多发起 2 个最关键的工具调用。
            - 当用户要求执行交易，而系统禁止直接执行时，请给出 command_suggestion。
            - 如果工具结果已经足够，下一轮应总结，不要重复调用相同工具。
            - 如果还需要更多信息，done=false，并给出 next_step。
            """;
    
    private final AiChatService aiChatService;
    private final BrokerService brokerService;
    private final BrokerAccountService brokerAccountService;
    private final QuoteService quoteService;
    private final TradeService tradeService;
    private final OkxService okxService;
    private final MonitorRepository monitorRepository;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    
    public FinancialAgentService(AiChatService aiChatService, BrokerService brokerService, 
                                 BrokerAccountService brokerAccountService, QuoteService quoteService, 
                                 TradeService tradeService, OkxService okxService,
                                 MonitorRepository monitorRepository, AppProperties properties, ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.brokerService = brokerService;
        this.brokerAccountService = brokerAccountService;
        this.quoteService = quoteService;
        this.tradeService = tradeService;
        this.okxService = okxService;
        this.monitorRepository = monitorRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }
    
    public Map<String, Object> handle(String message, String provider, String model, boolean autoExecute) {
        Map<String, Object> context = buildAgentContext();
        List<Map<String, Object>> traces = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        PlannerDecision latestDecision = null;
        String taskState = "created";
        boolean executed = false;
        Map<String, Object> latestTerminalResult = null;
        
        for (int round = 0; round < 4; round++) {
            String promptInput = buildPromptInput(message, context, observations, autoExecute, taskState, round + 1);
            String raw = aiChatService.generateText(promptInput, provider, model, AGENT_SYSTEM_PROMPT);
            if (raw == null || raw.isBlank()) {
                return Map.of("success", false, "message", "当前没有可用的AI模型，请先配置服务商、模型和 API Key");
            }
            
            latestDecision = parseDecision(raw);
            taskState = latestDecision.taskState();
            traces.add(Map.of(
                    "type", "plan",
                    "round", round + 1,
                    "raw", raw,
                    "intent", latestDecision.intent(),
                    "task_state", latestDecision.taskState(),
                    "risk_level", latestDecision.riskLevel(),
                    "done", latestDecision.done(),
                    "next_step", latestDecision.nextStep(),
                    "observation_summary", latestDecision.observationSummary(),
                    "tool_calls", latestDecision.toolCalls()
            ));
            
            if (latestDecision.done() || latestDecision.toolCalls().isEmpty()) {
                return buildAgentResult(latestDecision, traces, context, executed, latestTerminalResult);
            }
            
            for (ToolCall toolCall : latestDecision.toolCalls()) {
                Map<String, Object> toolResult = executeTool(toolCall, autoExecute);
                traces.add(Map.of(
                        "type", "tool",
                        "round", round + 1,
                        "tool", toolCall.name(),
                        "arguments", toolCall.arguments(),
                        "result", toolResult
                ));
                observations.add("工具 " + toolCall.name() + " 返回: " + safeJson(toolResult));
                
                if (Boolean.TRUE.equals(toolResult.get("executed"))) {
                    executed = true;
                }
                if (Boolean.TRUE.equals(toolResult.get("terminal"))) {
                    latestTerminalResult = toolResult;
                    String terminalMessage = String.valueOf(toolResult.getOrDefault("message", latestDecision.response()));
                    PlannerDecision terminalDecision = new PlannerDecision(
                            latestDecision.intent(),
                            latestDecision.taskState(),
                            terminalMessage,
                            latestDecision.riskLevel(),
                            true,
                            latestDecision.nextStep(),
                            latestDecision.observationSummary(),
                            latestDecision.requiresConfirmation(),
                            String.valueOf(toolResult.getOrDefault("command_suggestion", latestDecision.commandSuggestion())),
                            List.of()
                    );
                    return buildAgentResult(terminalDecision, traces, context, executed, toolResult);
                }
            }
        }
        
        if (latestDecision == null) {
            return Map.of("success", false, "message", "智能体未能生成有效响应");
        }
        return buildAgentResult(latestDecision, traces, context, executed, latestTerminalResult);
    }
    
    private Map<String, Object> buildAgentContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("broker_mode", brokerService.currentBrokerInfo());
        context.put("risk_rules", Map.of(
                "max_qty", properties.getRiskMaxQty(),
                "max_notional", properties.getRiskMaxNotional(),
                "allow_short", properties.isRiskAllowShort()
        ));
        context.put("monitor_summary", monitorRepository.getMonitorSummary());
        Object activeAccount = brokerAccountService.activeAccount();
        context.put("active_account", activeAccount == null ? Map.of() : activeAccount);
        return context;
    }
    
    private String buildPromptInput(String message, Map<String, Object> context, List<String> observations,
                                    boolean autoExecute, String taskState, int round) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户原始请求: ").append(message).append("\n\n");
        builder.append("当前轮次: ").append(round).append("\n");
        builder.append("当前任务状态: ").append(taskState).append("\n\n");
        builder.append("当前系统上下文: ").append(safeJson(context)).append("\n\n");
        builder.append("执行策略: ");
        builder.append(autoExecute
                ? "允许执行交易与策略工具，但仍需严格注意金融风险。"
                : "当前仅允许分析和建议，不允许直接执行交易类工具。");
        builder.append("\n\n");
        if (!observations.isEmpty()) {
            builder.append("前序观察:\n");
            for (String observation : observations) {
                builder.append("- ").append(observation).append("\n");
            }
            builder.append("\n");
        }
        builder.append("请根据当前上下文和观察，决定是否调用工具，并给出 JSON。");
        return builder.toString();
    }
    
    private PlannerDecision parseDecision(String raw) {
        try {
            String cleaned = raw.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("```\\s*$", "")
                    .trim();
            Map<String, Object> data = objectMapper.readValue(cleaned, new TypeReference<>() {});
            String intent = asString(data.get("intent"));
            String taskState = asString(data.get("task_state"));
            String response = asString(data.get("response"));
            String riskLevel = asString(data.get("risk_level"));
            boolean done = Boolean.TRUE.equals(data.get("done"));
            String nextStep = asString(data.get("next_step"));
            String observationSummary = asString(data.get("observation_summary"));
            boolean requiresConfirmation = Boolean.TRUE.equals(data.get("requires_confirmation"));
            String commandSuggestion = asString(data.get("command_suggestion"));
            List<ToolCall> toolCalls = new ArrayList<>();
            if (data.get("tool_calls") instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) continue;
                    String name = asString(map.get("name"));
                    Map<String, Object> args = map.get("arguments") instanceof Map<?, ?> argMap
                            ? castMap(argMap)
                            : Map.of();
                    if (name != null && !name.isBlank()) {
                        toolCalls.add(new ToolCall(name, args));
                    }
                }
            }
            return new PlannerDecision(
                    intent == null || intent.isBlank() ? "金融任务分析" : intent,
                    taskState == null || taskState.isBlank() ? "analyzing" : taskState,
                    response == null || response.isBlank() ? "已完成分析。" : response,
                    riskLevel == null || riskLevel.isBlank() ? "medium" : riskLevel,
                    done,
                    nextStep == null ? "" : nextStep,
                    observationSummary == null ? "" : observationSummary,
                    requiresConfirmation,
                    commandSuggestion == null ? "" : commandSuggestion,
                    toolCalls
            );
        } catch (Exception e) {
            return new PlannerDecision(
                    "金融任务分析",
                    "analyzing",
                    raw,
                    "medium",
                    true,
                    "",
                    "",
                    false,
                    "",
                    List.of()
            );
        }
    }
    
    private Map<String, Object> executeTool(ToolCall toolCall, boolean autoExecute) {
        String tool = toolCall.name().toLowerCase(Locale.ROOT);
        Map<String, Object> args = toolCall.arguments();
        
        return switch (tool) {
            case "get_broker_mode" -> Map.of("ok", true, "data", brokerService.currentBrokerInfo());
            case "get_risk_rules" -> Map.of("ok", true, "data", Map.of(
                    "max_qty", properties.getRiskMaxQty(),
                    "max_notional", properties.getRiskMaxNotional(),
                    "allow_short", properties.isRiskAllowShort()
            ));
            case "get_monitor_summary" -> Map.of("ok", true, "data", monitorRepository.getMonitorSummary());
            case "get_active_account" -> Map.of("ok", true, "data", brokerAccountService.activeAccount() == null ? Map.of() : brokerAccountService.activeAccount());
            case "search_market_quote" -> Map.of("ok", true, "data", quoteService.searchPublicQuotes(
                    asString(args.getOrDefault("query", args.getOrDefault("q", ""))),
                    intArg(args, "limit", 10),
                    resolveQuoteChannel()
            ));
            case "get_market_quote" -> Map.of("ok", true, "data", quoteService.getQuote(asString(args.get("symbol"))));
            case "get_market_kline" -> Map.of("ok", true, "data", quoteService.getPublicKlines(
                    asString(args.get("symbol")),
                    asString(args.getOrDefault("interval", "1d")),
                    intArg(args, "limit", 120)
            ));
            case "get_okx_portfolio" -> Map.of("ok", true, "data", okxService.getPortfolioSnapshot(intArg(args, "limit", 20)));
            case "run_trade_command" -> runTradeCommandTool(args, autoExecute);
            case "run_strategy" -> runStrategyTool(args, autoExecute);
            default -> Map.of("ok", false, "message", "unknown_tool:" + toolCall.name());
        };
    }
    
    private Map<String, Object> runTradeCommandTool(Map<String, Object> args, boolean autoExecute) {
        String command = asString(args.get("command"));
        if (command == null || command.isBlank()) {
            return Map.of("ok", false, "terminal", true, "message", "交易工具缺少 command 参数");
        }
        if (!autoExecute) {
            return Map.of(
                    "ok", true,
                    "terminal", true,
                    "executed", false,
                    "message", "当前为分析模式，我已生成建议指令，你确认后再执行更安全。",
                    "command_suggestion", command
            );
        }
        
        TradeCommandRequest cmdIn = new TradeCommandRequest(command, false);
        TradeCommandParseResult tradeResult = tradeService.parseTradeCommand(cmdIn);
        if (!tradeResult.ok()) {
            return Map.of("ok", false, "terminal", true, "message", tradeResult.message());
        }
        
        if ("manual_signal".equals(tradeResult.action())) {
            Map<String, Object> quote = quoteService.getQuote(tradeResult.symbol());
            SignalRequest signal = tradeService.buildManualSignal(
                    (BigDecimal) quote.get("price"),
                    tradeResult.side(),
                    tradeResult.symbol(),
                    tradeResult.quantity(),
                    tradeResult.strategyName()
            );
            SignalProcessResponse out = tradeService.processSignal(signal);
            return Map.of(
                    "ok", true,
                    "terminal", true,
                    "executed", out.riskPassed(),
                    "message", out.riskPassed() ? "交易指令已执行并进入订单流程。" : "交易指令已解析，但风控未通过。",
                    "data", out,
                    "command_suggestion", command
            );
        }
        
        if ("strategy_run".equals(tradeResult.action())) {
            return runStrategyTool(Map.of(
                    "symbol", tradeResult.symbol(),
                    "quantity", tradeResult.quantity(),
                    "strategy_name", tradeResult.strategyName()
            ), true);
        }
        
        return Map.of("ok", false, "terminal", true, "message", "不支持的交易动作");
    }
    
    private Map<String, Object> runStrategyTool(Map<String, Object> args, boolean autoExecute) {
        String symbol = asString(args.get("symbol"));
        if (symbol == null || symbol.isBlank()) {
            return Map.of("ok", false, "terminal", true, "message", "策略工具缺少 symbol 参数");
        }
        
        StrategyRunRequest request = new StrategyRunRequest(
                asString(args.getOrDefault("strategy_name", "sma_cmd_v1")),
                symbol,
                asString(args.getOrDefault("timeframe", "1d")),
                intArg(args, "short_window", 5),
                intArg(args, "long_window", 20),
                intArg(args, "quantity", 100)
        );
        
        if (!autoExecute) {
            String command = "运行策略 " + request.symbol() + " " + request.quantity();
            return Map.of(
                    "ok", true,
                    "terminal", true,
                    "executed", false,
                    "message", "当前为分析模式，我已生成策略执行建议，你确认后再执行更安全。",
                    "command_suggestion", command
            );
        }
        
        StrategySignalResult generated = tradeService.generateSignalFromSma(request);
        if (generated.signal() == null) {
            return Map.of(
                    "ok", true,
                    "terminal", true,
                    "executed", false,
                    "message", "策略已运行，但当前没有生成可执行信号。",
                    "data", generated
            );
        }
        SignalProcessResponse out = tradeService.processSignal(generated.signal());
        return Map.of(
                "ok", true,
                "terminal", true,
                "executed", out.riskPassed(),
                "message", out.riskPassed() ? "策略信号已执行并进入订单流程。" : "策略生成了信号，但风控未通过。",
                "data", out,
                "command_suggestion", "运行策略 " + request.symbol() + " " + request.quantity()
        );
    }
    
    private Map<String, Object> buildAgentResult(PlannerDecision decision, List<Map<String, Object>> traces,
                                                 Map<String, Object> context, boolean executed,
                                                 Map<String, Object> terminalResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", decision.response());
        result.put("command", decision.commandSuggestion().isBlank() ? null : decision.commandSuggestion());
        result.put("executed", executed);
        result.put("agent", Map.of(
                "intent", decision.intent(),
                "task_state", decision.taskState(),
                "risk_level", decision.riskLevel(),
                "done", decision.done(),
                "next_step", decision.nextStep(),
                "observation_summary", decision.observationSummary(),
                "requires_confirmation", decision.requiresConfirmation(),
                "context", context,
                "trace", traces
        ));
        if (terminalResult != null && terminalResult.get("data") != null) {
            result.put("data", terminalResult.get("data"));
        }
        return result;
    }
    
    private String resolveQuoteChannel() {
        String broker = String.valueOf(brokerService.currentBrokerInfo().getOrDefault("broker", "paper"));
        if ("okx".equalsIgnoreCase(broker)) return "okx";
        if ("usstock".equalsIgnoreCase(broker)) return "us";
        return "cn";
    }
    
    private int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object raw = args.get(key);
        if (raw == null) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    
    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        source.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }
    
    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
    
    private record PlannerDecision(
            String intent,
            String taskState,
            String response,
            String riskLevel,
            boolean done,
            String nextStep,
            String observationSummary,
            boolean requiresConfirmation,
            String commandSuggestion,
            List<ToolCall> toolCalls) {}
    
    private record ToolCall(String name, Map<String, Object> arguments) {}
}
