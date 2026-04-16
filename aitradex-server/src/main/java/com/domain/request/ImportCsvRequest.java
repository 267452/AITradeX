package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImportCsvRequest(
        @JsonProperty("csv_path") String csvPath,
        String timeframe,
        @JsonProperty("has_header") boolean hasHeader) {
}
