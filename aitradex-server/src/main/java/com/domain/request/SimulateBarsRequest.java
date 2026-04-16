package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record SimulateBarsRequest(
        String symbol,
        @JsonProperty("start_price") BigDecimal startPrice,
        int bars,
        String timeframe) {
}
