package com.domain.request;

import java.util.List;

public record AgentUpsertRequest(
        String name,
        String description,
        String icon,
        String status,
        String modelName,
        String systemPrompt,
        Double temperature,
        Integer maxTokens,
        String toolCallMode,
        List<Long> skillIds,
        List<Long> mcpToolIds,
        List<AgentKnowledgeConfig> knowledgeBases) {

    public record AgentKnowledgeConfig(
            Long knowledgeBaseId,
            Integer topK,
            Double scoreThreshold) {}
}
