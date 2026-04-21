package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MarketTickIngestRequest(
        @JsonProperty("items") List<MarketTickIngestItem> items) {
}
