package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SignalOrderIds(
        @JsonProperty("signal_id") Long signalId,
        @JsonProperty("order_id") Long orderId) {
}
