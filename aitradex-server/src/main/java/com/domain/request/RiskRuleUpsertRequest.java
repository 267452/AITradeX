package com.domain.request;

import java.util.Map;

public record RiskRuleUpsertRequest(
        String name,
        String ruleType,
        Map<String, Object> ruleConfig,
        boolean enabled,
        int priority
) {
}