package com.domain.request;

import java.util.List;

public record WorkflowGraphUpsertRequest(
        List<WorkflowGraphNodeUpsertRequest> nodes,
        List<WorkflowGraphEdgeUpsertRequest> edges) {
}
