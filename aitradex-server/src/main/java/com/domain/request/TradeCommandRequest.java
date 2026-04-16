package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TradeCommandRequest(String command, @JsonProperty("dry_run") boolean dryRun) {
}
