package com.domain.response;

public record AiConfigResponse(
        String provider,
        String model,
        String modelId,
        String baseUrl,
        boolean hasApiKey) {
}
