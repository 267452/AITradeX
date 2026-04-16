package com.domain.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowGraphEdgeUpsertRequest(
        String source,
        String target,
        String label) {
}
