package com.domain.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketTickStreamEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("source") String source,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("event_time") OffsetDateTime eventTime,
        @JsonProperty("recv_time") OffsetDateTime recvTime,
        @JsonProperty("last_price") BigDecimal lastPrice,
        @JsonProperty("bid1") BigDecimal bid1,
        @JsonProperty("ask1") BigDecimal ask1,
        @JsonProperty("volume") BigDecimal volume,
        @JsonProperty("turnover") BigDecimal turnover,
        @JsonProperty("source_event_id") String sourceEventId) {

    public static MarketTickStreamEvent of(String source,
                                           String exchange,
                                           String symbol,
                                           OffsetDateTime eventTime,
                                           OffsetDateTime recvTime,
                                           BigDecimal lastPrice,
                                           BigDecimal bid1,
                                           BigDecimal ask1,
                                           BigDecimal volume,
                                           BigDecimal turnover,
                                           String sourceEventId) {
        return new MarketTickStreamEvent(
                "market_tick",
                source,
                exchange,
                symbol,
                eventTime,
                recvTime,
                lastPrice,
                bid1,
                ask1,
                volume,
                turnover,
                sourceEventId);
    }
}
