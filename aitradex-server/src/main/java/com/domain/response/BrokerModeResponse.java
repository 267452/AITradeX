package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BrokerModeResponse(
        @JsonProperty("broker_mode") String brokerMode,
        String source,
        @JsonProperty("env_default") String envDefault) {
}
