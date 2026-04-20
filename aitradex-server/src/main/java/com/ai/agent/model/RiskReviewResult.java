package com.ai.agent.model;

import com.domain.request.SignalRequest;
import com.domain.request.StrategyRunRequest;
import java.util.LinkedHashMap;
import java.util.Map;

public record RiskReviewResult(
        String status,
        boolean applicable,
        boolean passed,
        String reason,
        String recommendation,
        SignalRequest proposedSignal,
        StrategyRunRequest strategyRequest,
        Map<String, Object> details) {

    public RiskReviewResult {
        details = details == null ? Map.of() : new LinkedHashMap<>(details);
    }
}
