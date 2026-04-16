package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record MonitorSummaryResponse(
        @JsonProperty("orders_total") Integer ordersTotal,
        @JsonProperty("orders_filled") Integer ordersFilled,
        @JsonProperty("orders_queued") Integer ordersQueued,
        @JsonProperty("positions_total") Integer positionsTotal,
        @JsonProperty("latest_equity") BigDecimal latestEquity,
        @JsonProperty("latest_cash") BigDecimal latestCash,
        @JsonProperty("latest_market_value") BigDecimal latestMarketValue,
        @JsonProperty("latest_order_time") OffsetDateTime latestOrderTime,
        @JsonProperty("recent_orders") List<OrderSummaryResponse> recentOrders) {
}
