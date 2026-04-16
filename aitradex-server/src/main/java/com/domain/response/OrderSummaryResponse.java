package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record OrderSummaryResponse(
        Long id,
        String symbol,
        String side,
        Integer quantity,
        String status,
        @JsonProperty("created_at") OffsetDateTime createdAt) {
}
