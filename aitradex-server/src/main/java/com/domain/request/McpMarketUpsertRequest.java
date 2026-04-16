package com.domain.request;

public record McpMarketUpsertRequest(
        String name,
        Integer packageCount,
        String visibility,
        String status,
        String refreshNote) {
}
