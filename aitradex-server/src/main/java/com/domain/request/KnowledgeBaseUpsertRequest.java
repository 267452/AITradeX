package com.domain.request;

import java.util.Map;

public record KnowledgeBaseUpsertRequest(
        String name,
        String description,
        String vectorStore,
        String embeddingModel,
        String status,
        Integer documentCount,
        Integer sliceCount,
        Map<String, Object> vectorConfig) {
}
