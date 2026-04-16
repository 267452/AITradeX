package com.domain.request;

public record SkillUpsertRequest(
        String name,
        String description,
        String icon,
        String category,
        String status,
        String promptTemplate,
        String[] variables,
        String[] tools,
        String enabledTools) {
}
