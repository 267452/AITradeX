package com.domain.entity;

import java.time.OffsetDateTime;

public record BrokerAccountEntity(
        Long id,
        String broker,
        String accountName,
        String baseUrl,
        Boolean enabled,
        Boolean active,
        OffsetDateTime createdAt,
        String apiKeyEncrypted,
        String apiSecretEncrypted,
        String accessTokenEncrypted,
        OffsetDateTime updatedAt) {
}
