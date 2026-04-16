package com.domain.request;

public record WorkflowDefinitionUpsertRequest(
        String name,
        String description,
        Integer versionNo,
        String status,
        Integer runCount,
        String category) {
}
