package com.domain.response;

public record AiTestResponse(
        boolean success,
        String message,
        String error) {
}
