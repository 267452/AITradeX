package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketTickIngestItem(
        @JsonProperty("source") String source,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("event_time") OffsetDateTime eventTime,
        @JsonProperty("last_price") BigDecimal lastPrice,
        @JsonProperty("bid1") BigDecimal bid1,
        @JsonProperty("ask1") BigDecimal ask1,
        @JsonProperty("volume") BigDecimal volume,
        @JsonProperty("turnover") BigDecimal turnover,
        @JsonProperty("source_event_id") String sourceEventId) {
}
