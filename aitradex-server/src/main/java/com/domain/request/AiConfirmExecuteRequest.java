package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiConfirmExecuteRequest(
        String command,
        String provider,
        String model,
        @JsonProperty("conversation_id") Long conversationId,
        @JsonProperty("workflow_id") Long workflowId,
        @JsonProperty("approval_passphrase") String approvalPassphrase,
        @JsonProperty("co_approver") String coApprover,
        @JsonProperty("approval_note") String approvalNote) {
}
