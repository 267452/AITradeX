package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SignalProcessResponse(
        @JsonProperty("signal_id") Long signalId,
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("risk_passed") boolean riskPassed,
        String message) {
}
