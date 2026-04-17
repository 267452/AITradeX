package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecutionContext(
        @JsonProperty("run_id") String runId,
        @JsonProperty("conversation_id") Long conversationId,
        @JsonProperty("workflow_id") Long workflowId,
        @JsonProperty("workflow_run_id") Long workflowRunId) {
}
