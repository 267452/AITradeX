package com.ai.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record AgentTraceStep(
        String role,
        String status,
        String summary,
        Map<String, Object> input,
        Map<String, Object> output) {

    public AgentTraceStep {
        input = input == null ? Map.of() : new LinkedHashMap<>(input);
        output = output == null ? Map.of() : new LinkedHashMap<>(output);
    }
}
