package com.domain.entity;

import java.time.OffsetDateTime;

public record AiConfigEntity(
        Long id,
        String provider,
        String model,
        String modelId,
        String apiKeyEncrypted,
        String baseUrl,
        Double temperature,
        Integer maxTokens,
        Boolean enabled,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
