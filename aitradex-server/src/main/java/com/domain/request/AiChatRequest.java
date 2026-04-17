package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiChatRequest(
        String message,
        String provider,
        String model,
        @JsonProperty("conversation_id") Long conversationId,
        @JsonProperty("workflow_id") Long workflowId) {
}
