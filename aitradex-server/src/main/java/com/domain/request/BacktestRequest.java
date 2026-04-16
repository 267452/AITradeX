package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record BacktestRequest(
        @JsonProperty("strategy_name") String strategyName,
        String symbol,
        String timeframe,
        @JsonProperty("short_window") int shortWindow,
        @JsonProperty("long_window") int longWindow,
        @JsonProperty("initial_cash") BigDecimal initialCash) {
}
