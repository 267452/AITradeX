package com.domain.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderStatusStreamEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("side") String side,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("status") String status,
        @JsonProperty("event_time") OffsetDateTime eventTime) {

    public static OrderStatusStreamEvent of(Long orderId,
                                            String symbol,
                                            String side,
                                            Integer quantity,
                                            BigDecimal price,
                                            String status,
                                            OffsetDateTime eventTime) {
        return new OrderStatusStreamEvent(
                "order_status",
                orderId,
                symbol,
                side,
                quantity,
                price,
                status,
                eventTime);
    }
}
