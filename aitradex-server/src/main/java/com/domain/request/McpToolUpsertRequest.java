package com.domain.request;

public record McpToolUpsertRequest(
        String name,
        String transportType,
        String endpoint,
        String category,
        String status,
        String note) {
}
