package com.repository;

import com.domain.response.FlinkComputeMetricsResponse;
import com.domain.response.FlinkHotSymbolResponse;
import com.domain.response.MonitorSummaryResponse;
import com.domain.response.OrderPageResponse;
import com.domain.response.OrderSummaryResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorRepository {
    private final JdbcTemplate jdbcTemplate;

    public MonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
