package com.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record FlinkComputeMetricsResponse(
        @JsonProperty("flink_enabled") Boolean flinkEnabled,
        @JsonProperty("engine_mode") String engineMode,
        @JsonProperty("job_name") String jobName,
        @JsonProperty("data_source") String dataSource,
        @JsonProperty("source_events_1m") Integer sourceEvents1m,
        @JsonProperty("source_events_5m") Integer sourceEvents5m,
        @JsonProperty("processed_events_1m") Integer processedEvents1m,
        @JsonProperty("processed_events_5m") Integer processedEvents5m,
        @JsonProperty("order_fill_rate_5m") BigDecimal orderFillRate5m,
        @JsonProperty("risk_reject_rate_5m") BigDecimal riskRejectRate5m,
        @JsonProperty("avg_workflow_latency_ms_5m") Long avgWorkflowLatencyMs5m,
        @JsonProperty("p95_workflow_latency_ms_5m") Long p95WorkflowLatencyMs5m,
        @JsonProperty("watermark_delay_ms") Long watermarkDelayMs,
        @JsonProperty("queued_orders_now") Integer queuedOrdersNow,
        @JsonProperty("active_runs_now") Integer activeRunsNow,
        @JsonProperty("completed_runs_5m") Integer completedRuns5m,
        @JsonProperty("failed_runs_5m") Integer failedRuns5m,
        @JsonProperty("hot_symbols") List<FlinkHotSymbolResponse> hotSymbols,
        @JsonProperty("last_compute_at") OffsetDateTime lastComputeAt) {
}
