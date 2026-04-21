package com.domain.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record WorkflowRunStreamEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("run_id") String runId,
        @JsonProperty("workflow_run_id") Long workflowRunId,
        @JsonProperty("workflow_id") Long workflowId,
        @JsonProperty("conversation_id") Long conversationId,
        @JsonProperty("status") String status,
        @JsonProperty("latency_ms") Long latencyMs,
        @JsonProperty("event_time") OffsetDateTime eventTime) {

    public static WorkflowRunStreamEvent of(String runId,
                                            Long workflowRunId,
                                            Long workflowId,
                                            Long conversationId,
                                            String status,
                                            Long latencyMs,
                                            OffsetDateTime eventTime) {
        return new WorkflowRunStreamEvent(
                "workflow_run",
                runId,
                workflowRunId,
                workflowId,
                conversationId,
                status,
                latencyMs,
                eventTime);
    }
}
