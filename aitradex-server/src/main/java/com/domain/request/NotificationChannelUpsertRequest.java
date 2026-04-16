package com.domain.request;

import java.util.Map;

public record NotificationChannelUpsertRequest(
        String name,
        String channelType,
        Map<String, Object> config,
        Boolean enabled) {
}
