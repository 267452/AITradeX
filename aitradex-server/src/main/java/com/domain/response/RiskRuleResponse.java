package com.domain.response;

import java.time.OffsetDateTime;
import java.util.Map;

public record RiskRuleResponse(
        long id,
        String name,
        String ruleType,
        Map<String, Object> ruleConfig,
        boolean enabled,
        int priority,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}