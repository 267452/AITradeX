package com.domain.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record RiskCheckStreamEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("check_name") String checkName,
        @JsonProperty("passed") Boolean passed,
        @JsonProperty("reason") String reason,
        @JsonProperty("event_time") OffsetDateTime eventTime) {

    public static RiskCheckStreamEvent of(String checkName, Boolean passed, String reason, OffsetDateTime eventTime) {
        return new RiskCheckStreamEvent("risk_check", checkName, passed, reason, eventTime);
    }
}
