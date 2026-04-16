package com.domain.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowGraphNodeUpsertRequest(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("node_name") String nodeName,
        @JsonProperty("node_type") String nodeType,
        Integer x,
        Integer y,
        @JsonProperty("sort_order") Integer sortOrder,
        @JsonProperty("config_note") String configNote) {
}
