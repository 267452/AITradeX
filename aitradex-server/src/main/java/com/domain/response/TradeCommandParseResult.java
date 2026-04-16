package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TradeCommandParseResult(
        boolean ok,
        String action,
        String message,
        String symbol,
        String side,
        Integer quantity,
        @JsonProperty("strategy_name") String strategyName) {
}
