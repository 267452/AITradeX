package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FlinkHotSymbolResponse(
        String symbol,
        @JsonProperty("order_count") Integer orderCount) {
}
