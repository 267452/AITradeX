package com.ai.agent.service;

import com.ai.agent.model.AgentTraceStep;
import com.ai.agent.model.ExecutionResult;
import com.ai.agent.model.MarketAnalysisResult;
import com.ai.agent.model.RiskReviewResult;
import com.ai.agent.model.TradingIntent;
import com.domain.request.ExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.BrokerAccountService;
import com.service.BrokerService;
import com.service.RiskService;
import com.service.WorkflowRuntimeService;
import com.service.WorkflowRuntimeService.WorkflowRunContext;
import com.repository.MonitorRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TradingDecisionOrchestrator {
    private final TradingIntentAgent tradingIntentAgent;
    private final MarketAnalysisAgent marketAnalysisAgent;
    private final RiskControlAgent riskControlAgent;
    private final TradingExecutionAgent tradingExecutionAgent;
    private final TradingResponseAgent tradingResponseAgent;
    private final WorkflowRuntimeService workflowRuntimeService;
    private final BrokerService brokerService;
    private final BrokerAccountService brokerAccountService;
    private final MonitorRepository monitorRepository;
    private final RiskService riskService;
    private final ObjectMapper objectMapper;

    public TradingDecisionOrchestrator(TradingIntentAgent tradingIntentAgent,
                                       MarketAnalysisAgent marketAnalysisAgent,
                                       RiskControlAgent riskControlAgent,
                                       TradingExecutionAgent tradingExecutionAgent,
                                       TradingResponseAgent tradingResponseAgent,
                                       WorkflowRuntimeService workflowRuntimeService,
                                       BrokerService brokerService,
                                       BrokerAccountService brokerAccountService,
                                       MonitorRepository monitorRepository,
                                       RiskService riskService,
                                       ObjectMapper objectMapper) {
        this.tradingIntentAgent = tradingIntentAgent;
        this.marketAnalysisAgent = marketAnalysisAgent;
        this.riskControlAgent = riskControlAgent;
        this.tradingExecutionAgent = tradingExecutionAgent;
        this.tradingResponseAgent = tradingResponseAgent;
        this.workflowRuntimeService = workflowRuntimeService;
        this.brokerService = brokerService;
        this.brokerAccountService = brokerAccountService;
        this.monitorRepository = monitorRepository;
        this.riskService = riskService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> handle(String message, String provider, String model,
                                      Long conversationId, Long workflowId, boolean autoExecute,
                                      Map<String, Object> requestContext) {
        WorkflowRunContext workflowRunContext = null;
        ExecutionContext executionContext = null;
        try {
            Map<String, Object> safeRequestContext = requestContext == null ? Map.of() : new LinkedHashMap<>(requestContext);
            workflowRunContext = workflowRuntimeService.startRun(
                    workflowId, conversationId, message, provider, model, autoExecute, safeRequestContext);
            executionContext = new ExecutionContext(
                    workflowRunContext.runId(),
                    conversationId,
                    workflowId,
                    workflowRunContext.workflowRunId());

            Map<String, Object> baseContext = buildBaseContext(executionContext);
            if (!safeRequestContext.isEmpty()) {
                baseContext.put("request_context", safeRequestContext);
            }
            List<AgentTraceStep> traces = new ArrayList<>();

            TradingIntent intent = tradingIntentAgent.resolve(message, provider, model, baseContext, autoExecute);
            appendTrace(traces, workflowRunContext, "intent_router", "completed", intent.summary(),
                    mutableMap("message", message, "auto_execute", autoExecute),
                    toMap(intent));

            MarketAnalysisResult marketAnalysis = marketAnalysisAgent.analyze(intent, baseContext);
            appendTrace(traces, workflowRunContext, "market_analyst", marketAnalysis.status(), marketAnalysis.summary(),
                    mutableMap("category", intent.category(), "symbol", intent.symbol()),
                    toMap(marketAnalysis));

            RiskReviewResult riskReview = riskControlAgent.review(intent, marketAnalysis, executionContext);
            appendTrace(traces, workflowRunContext, "risk_guardian", riskReview.status(), riskReview.recommendation(),
                    mutableMap("category", intent.category(), "symbol", intent.symbol()),
                    toMap(riskReview));

            ExecutionResult executionResult = tradingExecutionAgent.execute(intent, riskReview, autoExecute, executionContext);
            appendTrace(traces, workflowRunContext, "execution_agent", executionResult.status(), executionResult.message(),
                    mutableMap("auto_execute", autoExecute, "command", executionResult.commandSuggestion()),
                    toMap(executionResult));

            String responseMessage = tradingResponseAgent.compose(
                    message, provider, model, autoExecute, intent, marketAnalysis, riskReview, executionResult, baseContext);
            appendTrace(traces, workflowRunContext, "summary_agent", "completed", "已生成面向用户的决策总结",
                    Map.of("mode", autoExecute ? "execute" : "analysis"),
                    Map.of("message", responseMessage));

            Map<String, Object> result = buildResult(
                    responseMessage, intent, marketAnalysis, riskReview, executionResult,
                    baseContext, traces, autoExecute, executionContext, safeRequestContext);
            return completeRunAndAttachContext(workflowRunContext, executionContext, result);
        } catch (Exception e) {
            String errorMessage = e.getMessage() == null ? "agent_orchestrator_failed" : e.getMessage();
            return failRunAndAttachContext(workflowRunContext, executionContext, errorMessage);
        }
    }

    private Map<String, Object> buildBaseContext(ExecutionContext executionContext) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("broker_mode", brokerService.currentBrokerInfo());
        context.put("risk_rules", riskService.getEffectiveRiskConfigSnapshot());
        context.put("monitor_summary", monitorRepository.getMonitorSummary());
        Object activeAccount = brokerAccountService.activeAccount();
        context.put("active_account", activeAccount == null ? Map.of() : activeAccount);
        context.put("execution_context", buildExecutionContextMap(executionContext));
        return context;
    }

    private Map<String, Object> buildResult(String responseMessage,
                                            TradingIntent intent,
                                            MarketAnalysisResult marketAnalysis,
                                            RiskReviewResult riskReview,
                                            ExecutionResult executionResult,
                                            Map<String, Object> baseContext,
                                            List<AgentTraceStep> traces,
                                            boolean autoExecute,
                                            ExecutionContext executionContext,
                                            Map<String, Object> requestContext) {
        Map<String, Object> decisionCard = new LinkedHashMap<>();
        decisionCard.put("mode", autoExecute ? "execute" : "analysis");
        decisionCard.put("intent_category", intent.category());
        decisionCard.put("intent_summary", intent.summary());
        decisionCard.put("symbol", intent.symbol());
        decisionCard.put("command_suggestion", executionResult.commandSuggestion());
        decisionCard.put("decision", deriveDecision(executionResult, riskReview, autoExecute));
        decisionCard.put("market_bias", marketAnalysis.marketBias());
        decisionCard.put("market_confidence", marketAnalysis.confidence());
        decisionCard.put("risk_applicable", riskReview.applicable());
        decisionCard.put("risk_passed", riskReview.passed());
        decisionCard.put("risk_reason", riskReview.reason());
        decisionCard.put("execution_status", executionResult.status());
        decisionCard.put("executed", executionResult.executed());
        decisionCard.put("requires_confirmation", !autoExecute && riskReview.applicable());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", responseMessage);
        result.put("command", executionResult.commandSuggestion().isBlank() ? null : executionResult.commandSuggestion());
        result.put("executed", executionResult.executed());
        result.put("execution_context", buildExecutionContextMap(executionContext));
        Object approval = requestContext == null ? null : requestContext.get("execution_approval");
        if (approval instanceof Map<?, ?> approvalMap && !approvalMap.isEmpty()) {
            result.put("execution_approval", approvalMap);
            decisionCard.put("execution_approval", approvalMap);
        }
        result.put("agent", Map.of(
                "version", "multi-agent-v2",
                "mode", "agent_first_trading_decision",
                "decision_card", decisionCard,
                "context", baseContext,
                "intent", toMap(intent),
                "market_analysis", toMap(marketAnalysis),
                "risk_review", toMap(riskReview),
                "execution_result", toMap(executionResult),
                "trace", traces.stream().map(this::toMap).toList()
        ));

        Map<String, Object> data = new LinkedHashMap<>();
        if (!executionResult.data().isEmpty()) {
            data.putAll(executionResult.data());
        }
        if (data.isEmpty()) {
            if (intent.isAccountReview()) {
                data.put("monitor_summary", baseContext.get("monitor_summary"));
                data.put("active_account", baseContext.get("active_account"));
            } else {
                data.put("market_analysis", toMap(marketAnalysis));
                data.put("risk_review", toMap(riskReview));
            }
        }
        result.put("data", data);
        return result;
    }

    private String deriveDecision(ExecutionResult executionResult, RiskReviewResult riskReview, boolean autoExecute) {
        if (executionResult.executed()) {
            return "executed";
        }
        if ("blocked".equals(executionResult.status())) {
            return "blocked";
        }
        if (riskReview.applicable() && !autoExecute) {
            return "ready_to_execute";
        }
        return "observe";
    }

    private void appendTrace(List<AgentTraceStep> traces, WorkflowRunContext context, String role, String status,
                             String summary, Map<String, Object> input, Map<String, Object> output) {
        AgentTraceStep step = new AgentTraceStep(role, status, summary, input, output);
        traces.add(step);
        workflowRuntimeService.recordAgentStep(context, role, status, input, output, summary);
    }

    private Map<String, Object> completeRunAndAttachContext(
            WorkflowRunContext workflowRunContext,
            ExecutionContext executionContext,
            Map<String, Object> result) {
        Map<String, Object> output = attachExecutionContext(result, executionContext);
        workflowRuntimeService.completeRun(workflowRunContext, output);
        return output;
    }

    private Map<String, Object> failRunAndAttachContext(
            WorkflowRunContext workflowRunContext,
            ExecutionContext executionContext,
            String errorMessage) {
        if (workflowRunContext != null) {
            workflowRuntimeService.failRun(workflowRunContext, errorMessage);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", errorMessage);
        return attachExecutionContext(result, executionContext);
    }

    private Map<String, Object> attachExecutionContext(Map<String, Object> payload, ExecutionContext executionContext) {
        if (executionContext == null) {
            return payload;
        }
        Map<String, Object> output = new LinkedHashMap<>(payload);
        output.put("run_id", executionContext.runId());
        output.put("workflow_run_id", executionContext.workflowRunId());
        output.put("conversation_id", executionContext.conversationId());
        output.put("workflow_id", executionContext.workflowId());
        output.put("execution_context", buildExecutionContextMap(executionContext));
        return output;
    }

    private Map<String, Object> buildExecutionContextMap(ExecutionContext executionContext) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (executionContext == null) {
            context.put("run_id", null);
            context.put("workflow_run_id", null);
            context.put("conversation_id", null);
            context.put("workflow_id", null);
            return context;
        }
        context.put("run_id", executionContext.runId());
        context.put("workflow_run_id", executionContext.workflowRunId());
        context.put("conversation_id", executionContext.conversationId());
        context.put("workflow_id", executionContext.workflowId());
        return context;
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    private Map<String, Object> mutableMap(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}
