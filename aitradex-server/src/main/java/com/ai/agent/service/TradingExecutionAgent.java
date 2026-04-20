package com.ai.agent.service;

import com.ai.agent.model.ExecutionResult;
import com.ai.agent.model.RiskReviewResult;
import com.ai.agent.model.TradingIntent;
import com.domain.request.ExecutionContext;
import com.domain.response.SignalProcessResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.TradeService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TradingExecutionAgent {
    private final TradeService tradeService;
    private final ObjectMapper objectMapper;

    public TradingExecutionAgent(TradeService tradeService, ObjectMapper objectMapper) {
        this.tradeService = tradeService;
        this.objectMapper = objectMapper;
    }

    public ExecutionResult execute(TradingIntent intent, RiskReviewResult riskReview,
                                   boolean autoExecute, ExecutionContext executionContext) {
        String commandSuggestion = resolveCommandSuggestion(intent, riskReview);

        if (!riskReview.applicable()) {
            return new ExecutionResult(
                    "skipped",
                    false,
                    "当前请求不涉及实际下单执行。",
                    commandSuggestion,
                    Map.of());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        if (riskReview.proposedSignal() != null) {
            data.put("proposed_signal", toMap(riskReview.proposedSignal()));
        }
        if (!riskReview.details().isEmpty()) {
            data.put("risk_review", riskReview.details());
        }

        if (!autoExecute) {
            return new ExecutionResult(
                    "analysis_only",
                    false,
                    "当前为分析模式，已生成可执行建议，但尚未发单。",
                    commandSuggestion,
                    data);
        }

        if (!riskReview.passed() || riskReview.proposedSignal() == null) {
            return new ExecutionResult(
                    "blocked",
                    false,
                    "风险预检未通过，系统已阻止直接执行。",
                    commandSuggestion,
                    data);
        }

        SignalProcessResponse out = tradeService.processSignal(riskReview.proposedSignal(), executionContext);
        data.put("signal_process", toMap(out));

        String message = out.riskPassed()
                ? "执行 agent 已将交易信号送入订单链路。"
                : "执行阶段再次被风控拦截，未进入最终下单。";
        return new ExecutionResult(
                out.riskPassed() ? "executed" : "blocked",
                out.riskPassed(),
                message,
                commandSuggestion,
                data);
    }

    private String resolveCommandSuggestion(TradingIntent intent, RiskReviewResult riskReview) {
        if (intent.commandSuggestion() != null && !intent.commandSuggestion().isBlank()) {
            return intent.commandSuggestion();
        }
        if (riskReview.strategyRequest() != null) {
            return "运行策略 " + riskReview.strategyRequest().symbol() + " " + riskReview.strategyRequest().quantity();
        }
        if (riskReview.proposedSignal() != null) {
            return ("buy".equals(riskReview.proposedSignal().side()) ? "买入 " : "卖出 ")
                    + riskReview.proposedSignal().symbol()
                    + " "
                    + riskReview.proposedSignal().quantity();
        }
        return "";
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }
}
