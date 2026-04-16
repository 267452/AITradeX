package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record OrderDetailResponse(
        Long id,
        String symbol,
        String side,
        @JsonProperty("order_type") String orderType,
        BigDecimal price,
        Integer quantity,
        String status,
        @JsonProperty("strategy_name") String strategyName) {
}
