package com.ai.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record MarketAnalysisResult(
        String status,
        String summary,
        String marketBias,
        Double confidence,
        Map<String, Object> facts,
        Map<String, Object> highlights) {

    public MarketAnalysisResult {
        facts = facts == null ? Map.of() : new LinkedHashMap<>(facts);
        highlights = highlights == null ? Map.of() : new LinkedHashMap<>(highlights);
    }
}
