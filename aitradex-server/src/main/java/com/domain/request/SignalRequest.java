package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SignalRequest(
        @JsonProperty("strategy_name") String strategyName,
        String symbol,
        String side,
        @JsonProperty("signal_strength") BigDecimal signalStrength,
        BigDecimal price,
        int quantity,
        @JsonProperty("signal_time") OffsetDateTime signalTime) {
}
