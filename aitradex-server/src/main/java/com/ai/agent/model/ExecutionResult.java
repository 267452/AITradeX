package com.ai.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ExecutionResult(
        String status,
        boolean executed,
        String message,
        String commandSuggestion,
        Map<String, Object> data) {

    public ExecutionResult {
        data = data == null ? Map.of() : new LinkedHashMap<>(data);
    }
}
