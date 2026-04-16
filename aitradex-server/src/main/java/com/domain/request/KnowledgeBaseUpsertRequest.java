package com.domain.request;

public record KnowledgeBaseUpsertRequest(
        String name,
        String description,
        String vectorStore,
        String embeddingModel,
        String status,
        Integer documentCount,
        Integer sliceCount) {
}
