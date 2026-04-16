package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BrokerAccountCreateRequest(
        String broker,
        @JsonProperty("account_name") String accountName,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("api_secret") String apiSecret,
        @JsonProperty("access_token") String accessToken) {
}
