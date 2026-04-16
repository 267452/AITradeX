package com.domain.request;

public record ConversationSessionUpsertRequest(
        String sessionCode,
        String title,
        String channel,
        String modelName,
        Integer roundCount,
        Double userRating,
        Integer toolCalls,
        Integer handoffCount,
        Double knowledgeHitRate,
        String status) {
}
