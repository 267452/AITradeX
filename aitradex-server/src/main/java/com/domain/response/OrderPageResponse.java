package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OrderPageResponse(
        Integer total,
        Integer page,
        @JsonProperty("page_size") Integer pageSize,
        List<OrderSummaryResponse> items) {
}
