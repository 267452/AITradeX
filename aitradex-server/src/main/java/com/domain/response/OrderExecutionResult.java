package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderExecutionResult(
        boolean ok,
        String reason,
        @JsonProperty("order_id") Long orderId,
        String status) {
}
