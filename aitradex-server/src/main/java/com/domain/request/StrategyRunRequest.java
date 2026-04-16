package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StrategyRunRequest(
        @JsonProperty("strategy_name") String strategyName,
        String symbol,
        String timeframe,
        @JsonProperty("short_window") int shortWindow,
        @JsonProperty("long_window") int longWindow,
        int quantity) {
}
