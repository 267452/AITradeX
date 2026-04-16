package com.domain.response;

public record AiSwitchModelResponse(
        boolean success,
        String message,
        String provider,
        String model) {
}
