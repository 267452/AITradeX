package com.repository;

import com.domain.response.FlinkComputeMetricsResponse;
import com.domain.response.FlinkHotSymbolResponse;
import com.domain.response.MonitorSummaryResponse;
import com.domain.response.OrderPageResponse;
import com.domain.response.OrderSummaryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MonitorRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public MonitorSummaryResponse getMonitorSummary() {
        Map<String, Object> totals = jdbcTemplate.queryForMap("""
                SELECT
                  COUNT(*)::int AS orders_total,
                  COUNT(*) FILTER (WHERE status = 'filled')::int AS orders_filled,
                  COUNT(*) FILTER (WHERE status = 'queued')::int AS orders_queued,
                  MAX(created_at) AS latest_order_time
                FROM trade_order
                """);
        Integer positionsTotal = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)::int
                FROM (
                  SELECT symbol, MAX(snapshot_time) AS max_time
                  FROM position_snapshot
                  GROUP BY symbol
                ) p
                """, Integer.class);
        List<Map<String, Object>> eqRows = jdbcTemplate.queryForList("""
                SELECT cash_balance, market_value, total_equity
                FROM account_snapshot
                ORDER BY snapshot_time DESC
                LIMIT 1
                """);
        Map<String, Object> eq = eqRows.isEmpty() ? null : eqRows.get(0);
        List<Map<String, Object>> recentOrders = jdbcTemplate.queryForList("""
                SELECT id, symbol, side, quantity, status, created_at
                FROM trade_order
                ORDER BY created_at DESC
                LIMIT 20
                """);
        return new MonitorSummaryResponse(
                (Integer) totals.getOrDefault("orders_total", 0),
                (Integer) totals.getOrDefault("orders_filled", 0),
                (Integer) totals.getOrDefault("orders_queued", 0),
                positionsTotal == null ? 0 : positionsTotal,
                eq == null ? BigDecimal.ZERO : (BigDecimal) eq.get("total_equity"),
                eq == null ? BigDecimal.ZERO : (BigDecimal) eq.get("cash_balance"),
                eq == null ? BigDecimal.ZERO : (BigDecimal) eq.get("market_value"),
                toOffsetDateTime(totals.get("latest_order_time")),
                recentOrders.stream().map(this::toOrderSummary).toList());
    }

    public OrderPageResponse listOrdersPaginated(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safeSize;
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*)::int FROM trade_order", Integer.class);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, symbol, side, quantity, status, created_at
                FROM trade_order
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, safeSize, offset);
        return new OrderPageResponse(
                total == null ? 0 : total,
                safePage,
                safeSize,
                rows.stream().map(this::toOrderSummary).toList());
    }

    public FlinkComputeMetricsResponse getFlinkComputeMetrics(boolean flinkEnabled, String engineMode, String jobName) {
        String normalizedEngine = engineMode == null || engineMode.isBlank() ? "snapshot" : engineMode;
        String normalizedJobName = jobName == null || jobName.isBlank() ? "aitradex-flink-compute" : jobName;
        if (!flinkEnabled) {
            return emptyFlinkMetrics(false, normalizedEngine, normalizedJobName, "flink_disabled");
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT
                        id,
                        source_events_1m,
                        source_events_5m,
                        processed_events_1m,
                        processed_events_5m,
                        order_fill_rate_5m,
                        risk_reject_rate_5m,
                        avg_workflow_latency_ms_5m,
                        p95_workflow_latency_ms_5m,
                        watermark_delay_ms,
                        queued_orders_now,
                        active_runs_now,
                        completed_runs_5m,
                        failed_runs_5m,
                        computed_at
                    FROM flink_compute_snapshot
                    ORDER BY computed_at DESC
                    LIMIT 1
                    """);
            if (rows.isEmpty()) {
                return emptyFlinkMetrics(true, normalizedEngine, normalizedJobName, "flink_snapshot_missing");
            }

            Map<String, Object> snapshot = rows.get(0);
            Long snapshotId = toLong(snapshot.get("id"));
            List<FlinkHotSymbolResponse> hotSymbols = snapshotId == null
                    ? List.of()
                    : jdbcTemplate.query("""
                            SELECT symbol, order_count
                            FROM flink_hot_symbol_snapshot
                            WHERE snapshot_id = ?
                            ORDER BY rank_no ASC, order_count DESC, symbol ASC
                            LIMIT 6
                            """, (rs, rowNum) -> new FlinkHotSymbolResponse(
                            rs.getString("symbol"),
                            rs.getInt("order_count")), snapshotId);

            return new FlinkComputeMetricsResponse(
                    true,
                    normalizedEngine,
                    normalizedJobName,
                    "flink_snapshot",
                    toInt(snapshot.get("source_events_1m")),
                    toInt(snapshot.get("source_events_5m")),
                    toInt(snapshot.get("processed_events_1m")),
                    toInt(snapshot.get("processed_events_5m")),
                    toDecimal(snapshot.get("order_fill_rate_5m")),
                    toDecimal(snapshot.get("risk_reject_rate_5m")),
                    toLong(snapshot.get("avg_workflow_latency_ms_5m")),
                    toLong(snapshot.get("p95_workflow_latency_ms_5m")),
                    toLong(snapshot.get("watermark_delay_ms")),
                    toInt(snapshot.get("queued_orders_now")),
                    toInt(snapshot.get("active_runs_now")),
                    toInt(snapshot.get("completed_runs_5m")),
                    toInt(snapshot.get("failed_runs_5m")),
                    hotSymbols,
                    toOffsetDateTime(snapshot.get("computed_at")));
        } catch (Exception e) {
            return emptyFlinkMetrics(true, normalizedEngine, normalizedJobName, "flink_snapshot_unavailable");
        }
    }

    public List<Map<String, Object>> listWorkflowRuns(int limit,
                                                      String runId,
                                                      Long workflowRunId,
                                                      String status,
                                                      OffsetDateTime startedFrom,
                                                      OffsetDateTime startedTo) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        String safeRunId = runId == null ? "" : runId.trim();
        String safeStatus = status == null ? "" : status.trim();

        StringBuilder sql = new StringBuilder("""
                SELECT id, run_id, workflow_id, conversation_id, status, error_message,
                       input_payload, output_payload, started_at, finished_at
                FROM workflow_run
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (workflowRunId != null && workflowRunId > 0) {
            sql.append(" AND id = ? ");
            params.add(workflowRunId);
        }
        if (!safeRunId.isEmpty()) {
            sql.append(" AND run_id ILIKE ? ");
            params.add("%" + safeRunId + "%");
        }
        if (!safeStatus.isEmpty()) {
            sql.append(" AND LOWER(status) = LOWER(?) ");
            params.add(safeStatus);
        }
        if (startedFrom != null) {
            sql.append(" AND started_at >= ? ");
            params.add(startedFrom);
        }
        if (startedTo != null) {
            sql.append(" AND started_at <= ? ");
            params.add(startedTo);
        }
        sql.append(" ORDER BY started_at DESC LIMIT ? ");
        params.add(safeLimit);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> inputPayload = asMap(row.get("input_payload"));
            Map<String, Object> outputPayload = asMap(row.get("output_payload"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("workflow_run_id", toLong(row.get("id")));
            item.put("run_id", row.get("run_id"));
            item.put("workflow_id", toLong(row.get("workflow_id")));
            item.put("conversation_id", toLong(row.get("conversation_id")));
            item.put("status", String.valueOf(row.getOrDefault("status", "unknown")));
            item.put("error_message", String.valueOf(row.getOrDefault("error_message", "")));
            item.put("started_at", toOffsetDateTime(row.get("started_at")));
            item.put("finished_at", toOffsetDateTime(row.get("finished_at")));
            item.put("message", getNestedValue(outputPayload, "message"));
            item.put("executed", getNestedValue(outputPayload, "executed"));
            item.put("decision", getNestedValue(outputPayload, "agent", "decision_card", "decision"));
            item.put("intent_category", getNestedValue(outputPayload, "agent", "decision_card", "intent_category"));
            item.put("risk_reason", getNestedValue(outputPayload, "agent", "decision_card", "risk_reason"));
            item.put("auto_execute", getNestedValue(inputPayload, "auto_execute"));
            out.add(item);
        }
        return out;
    }

    public Map<String, Object> getWorkflowRunDetail(String runId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, run_id, workflow_id, conversation_id, status, error_message,
                       input_payload, output_payload, started_at, finished_at
                FROM workflow_run
                WHERE run_id = ?
                LIMIT 1
                """, runId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        long workflowRunId = toLong(row.get("id"));

        List<Map<String, Object>> stepRows = jdbcTemplate.queryForList("""
                SELECT step_order, node_id, node_name, node_type, status, error_message,
                       input_payload, output_payload, started_at, finished_at
                FROM workflow_run_step
                WHERE workflow_run_id = ?
                ORDER BY step_order ASC
                """, workflowRunId);

        List<Map<String, Object>> steps = new ArrayList<>();
        for (Map<String, Object> stepRow : stepRows) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step_order", toInt(stepRow.get("step_order")));
            step.put("node_id", stepRow.get("node_id"));
            step.put("node_name", stepRow.get("node_name"));
            step.put("node_type", stepRow.get("node_type"));
            step.put("status", stepRow.get("status"));
            step.put("error_message", stepRow.get("error_message"));
            step.put("input_payload", asMap(stepRow.get("input_payload")));
            step.put("output_payload", asMap(stepRow.get("output_payload")));
            step.put("started_at", toOffsetDateTime(stepRow.get("started_at")));
            step.put("finished_at", toOffsetDateTime(stepRow.get("finished_at")));
            steps.add(step);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("workflow_run_id", workflowRunId);
        detail.put("run_id", row.get("run_id"));
        detail.put("workflow_id", toLong(row.get("workflow_id")));
        detail.put("conversation_id", toLong(row.get("conversation_id")));
        detail.put("status", row.get("status"));
        detail.put("error_message", row.get("error_message"));
        detail.put("started_at", toOffsetDateTime(row.get("started_at")));
        detail.put("finished_at", toOffsetDateTime(row.get("finished_at")));
        detail.put("input_payload", asMap(row.get("input_payload")));
        detail.put("output_payload", asMap(row.get("output_payload")));
        detail.put("steps", steps);
        return detail;
    }

    public Map<String, Object> getWorkflowQualityMetrics(int windowHours, int bucketMinutes, int limit) {
        int safeWindowHours = Math.max(1, Math.min(24 * 30, windowHours));
        int safeBucketMinutes = Math.max(5, Math.min(240, bucketMinutes));
        int safeLimit = Math.max(6, Math.min(500, limit));

        List<Map<String, Object>> summaryRows = jdbcTemplate.queryForList("""
                WITH base AS (
                    SELECT
                        status,
                        CASE
                            WHEN finished_at IS NULL OR started_at IS NULL THEN NULL
                            ELSE GREATEST(0, EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000)
                        END AS latency_ms,
                        LOWER(COALESCE(output_payload -> 'agent' -> 'decision_card' ->> 'risk_passed', '')) AS risk_passed
                    FROM workflow_run
                    WHERE started_at >= NOW() - (? * INTERVAL '1 hour')
                )
                SELECT
                    COUNT(*)::int AS run_count,
                    COALESCE(ROUND((100.0 * COUNT(*) FILTER (WHERE status = 'completed') / NULLIF(COUNT(*), 0))::numeric, 2), 0) AS success_rate,
                    COALESCE(ROUND((100.0 * COUNT(*) FILTER (WHERE risk_passed = 'false') / NULLIF(COUNT(*), 0))::numeric, 2), 0) AS risk_reject_rate,
                    COALESCE(ROUND(AVG(latency_ms)::numeric, 2), 0) AS avg_latency_ms,
                    COALESCE(ROUND((PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms))::numeric, 2), 0) AS p95_latency_ms
                FROM base
                """, safeWindowHours);
        Map<String, Object> summary = summaryRows.isEmpty() ? Map.of() : summaryRows.get(0);

        List<Map<String, Object>> trendRows = jdbcTemplate.queryForList("""
                WITH base AS (
                    SELECT
                        to_timestamp(
                            FLOOR(EXTRACT(EPOCH FROM started_at) / (? * 60)) * (? * 60)
                        ) AS bucket_start,
                        status,
                        CASE
                            WHEN finished_at IS NULL OR started_at IS NULL THEN NULL
                            ELSE GREATEST(0, EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000)
                        END AS latency_ms,
                        LOWER(COALESCE(output_payload -> 'agent' -> 'decision_card' ->> 'risk_passed', '')) AS risk_passed
                    FROM workflow_run
                    WHERE started_at >= NOW() - (? * INTERVAL '1 hour')
                )
                SELECT
                    bucket_start,
                    COUNT(*)::int AS run_count,
                    COALESCE(ROUND((100.0 * COUNT(*) FILTER (WHERE status = 'completed') / NULLIF(COUNT(*), 0))::numeric, 2), 0) AS success_rate,
                    COALESCE(ROUND((100.0 * COUNT(*) FILTER (WHERE risk_passed = 'false') / NULLIF(COUNT(*), 0))::numeric, 2), 0) AS risk_reject_rate,
                    COALESCE(ROUND(AVG(latency_ms)::numeric, 2), 0) AS avg_latency_ms,
                    COALESCE(ROUND((PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms))::numeric, 2), 0) AS p95_latency_ms
                FROM base
                GROUP BY bucket_start
                ORDER BY bucket_start ASC
                LIMIT ?
                """,
                safeBucketMinutes,
                safeBucketMinutes,
                safeWindowHours,
                safeLimit);

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> row : trendRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bucket_start", toOffsetDateTime(row.get("bucket_start")));
            item.put("run_count", toInt(row.get("run_count")));
            item.put("success_rate", toDouble(row.get("success_rate")));
            item.put("risk_reject_rate", toDouble(row.get("risk_reject_rate")));
            item.put("avg_latency_ms", toDouble(row.get("avg_latency_ms")));
            item.put("p95_latency_ms", toDouble(row.get("p95_latency_ms")));
            trend.add(item);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("window_hours", safeWindowHours);
        output.put("bucket_minutes", safeBucketMinutes);
        output.put("run_count", toInt(summary.get("run_count")));
        output.put("success_rate", toDouble(summary.get("success_rate")));
        output.put("risk_reject_rate", toDouble(summary.get("risk_reject_rate")));
        output.put("avg_latency_ms", toDouble(summary.get("avg_latency_ms")));
        output.put("p95_latency_ms", toDouble(summary.get("p95_latency_ms")));
        output.put("trend", trend);
        output.put("generated_at", OffsetDateTime.now(ZoneOffset.UTC));
        return output;
    }

    private OrderSummaryResponse toOrderSummary(Map<String, Object> row) {
        return new OrderSummaryResponse(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("symbol")),
                String.valueOf(row.get("side")),
                ((Number) row.get("quantity")).intValue(),
                String.valueOf(row.get("status")),
                toOffsetDateTime(row.get("created_at")));
    }

    private FlinkComputeMetricsResponse emptyFlinkMetrics(boolean flinkEnabled, String engineMode, String jobName,
                                                          String dataSource) {
        return new FlinkComputeMetricsResponse(
                flinkEnabled,
                engineMode,
                jobName,
                dataSource,
                0,
                0,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                0L,
                0L,
                0,
                0,
                0,
                0,
                List.of(),
                null);
    }

    private Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    private Object getNestedValue(Map<String, Object> source, String... keys) {
        Object cursor = source;
        for (String key : keys) {
            if (!(cursor instanceof Map<?, ?> map)) {
                return null;
            }
            cursor = map.get(key);
        }
        return cursor;
    }

    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("_raw", String.valueOf(value));
        }
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
