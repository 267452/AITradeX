package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record BrokerAccountResponse(
        Long id,
        String broker,
        @JsonProperty("account_name") String accountName,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("api_key_masked") String apiKeyMasked,
        boolean enabled,
        @JsonProperty("is_active") boolean active,
        @JsonProperty("created_at") OffsetDateTime createdAt) {
}
