package com.ai.service;

import com.ai.agent.service.TradingDecisionOrchestrator;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FinancialAgentService {
    private final TradingDecisionOrchestrator tradingDecisionOrchestrator;

    public FinancialAgentService(TradingDecisionOrchestrator tradingDecisionOrchestrator) {
        this.tradingDecisionOrchestrator = tradingDecisionOrchestrator;
    }

    public Map<String, Object> handle(String message, String provider, String model,
                                      Long conversationId, Long workflowId, boolean autoExecute) {
        return handle(message, provider, model, conversationId, workflowId, autoExecute, Map.of());
    }

    public Map<String, Object> handle(String message, String provider, String model,
                                      Long conversationId, Long workflowId, boolean autoExecute,
                                      Map<String, Object> requestContext) {
        return tradingDecisionOrchestrator.handle(
                message,
                provider,
                model,
                conversationId,
                workflowId,
                autoExecute,
                requestContext == null ? Map.of() : requestContext);
    }
}
