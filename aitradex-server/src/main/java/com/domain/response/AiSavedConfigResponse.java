package com.domain.response;

import java.time.OffsetDateTime;

public record AiSavedConfigResponse(
        Long id,
        String provider,
        String model,
        String modelId,
        String baseUrl,
        Boolean active,
        OffsetDateTime updatedAt) {
}
