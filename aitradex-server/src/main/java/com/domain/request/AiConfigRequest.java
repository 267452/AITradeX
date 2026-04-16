package com.domain.request;

public record AiConfigRequest(
        String provider,
        String model,
        String modelId,
        String apiKey,
        String baseUrl,
        String message) {
}
