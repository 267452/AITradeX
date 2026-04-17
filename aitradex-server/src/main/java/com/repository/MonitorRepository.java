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
                (java.time.OffsetDateTime) totals.get("latest_order_time"),
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
        Integer sourceEvents1m = safeQueryInt("""
                SELECT COUNT(*)::int
                FROM workflow_run_step
                WHERE created_at >= NOW() - INTERVAL '1 minute'
                """);
        Integer sourceEvents5m = safeQueryInt("""
                SELECT COUNT(*)::int
                FROM workflow_run_step
                WHERE created_at >= NOW() - INTERVAL '5 minutes'
                """);
        Integer processedEvents1m = safeQueryInt("""
                SELECT (
                    (SELECT COUNT(*)::int FROM trade_order WHERE created_at >= NOW() - INTERVAL '1 minute') +
                    (SELECT COUNT(*)::int FROM risk_check_log WHERE created_at >= NOW() - INTERVAL '1 minute')
                )::int
                """);
        Integer processedEvents5m = safeQueryInt("""
                SELECT (
                    (SELECT COUNT(*)::int FROM trade_order WHERE created_at >= NOW() - INTERVAL '5 minutes') +
                    (SELECT COUNT(*)::int FROM risk_check_log WHERE created_at >= NOW() - INTERVAL '5 minutes')
                )::int
                """);
        BigDecimal orderFillRate5m = safeQueryDecimal("""
                SELECT CASE
                    WHEN COUNT(*) = 0 THEN 0::numeric
                    ELSE ROUND((COUNT(*) FILTER (WHERE status = 'filled')::numeric / COUNT(*)::numeric) * 100, 2)
                END
                FROM trade_order
                WHERE created_at >= NOW() - INTERVAL '5 minutes'
                """);
        BigDecimal riskRejectRate5m = safeQueryDecimal("""
                SELECT CASE
                    WHEN COUNT(*) = 0 THEN 0::numeric
                    ELSE ROUND((COUNT(*) FILTER (WHERE passed = false)::numeric / COUNT(*)::numeric) * 100, 2)
                END
                FROM risk_check_log
                WHERE created_at >= NOW() - INTERVAL '5 minutes'
                """);
        Long avgWorkflowLatencyMs5m = safeQueryLong("""
                SELECT COALESCE(
                    ROUND(AVG(EXTRACT(EPOCH FROM (COALESCE(finished_at, NOW()) - started_at)) * 1000)),
                    0
                )::bigint
                FROM workflow_run
                WHERE started_at >= NOW() - INTERVAL '5 minutes'
                """);
        Long p95WorkflowLatencyMs5m = safeQueryLong("""
                SELECT COALESCE(
                    ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (
                        ORDER BY EXTRACT(EPOCH FROM (COALESCE(finished_at, NOW()) - started_at)) * 1000
                    )),
                    0
                )::bigint
                FROM workflow_run
                WHERE started_at >= NOW() - INTERVAL '5 minutes'
                """);
        Long watermarkDelayMs = safeQueryLong("""
                SELECT COALESCE(
                    ROUND(EXTRACT(EPOCH FROM (NOW() - MAX(COALESCE(finished_at, created_at)))) * 1000),
                    0
                )::bigint
                FROM workflow_run_step
                """);
        Integer queuedOrdersNow = safeQueryInt("""
                SELECT COUNT(*)::int
                FROM trade_order
                WHERE status = 'queued'
                """);
        Integer activeRunsNow = safeQueryInt("""
                SELECT COUNT(*)::int
                FROM workflow_run
                WHERE status = 'running'
                """);
        Integer completedRuns5m = safeQueryInt("""
                SELECT COUNT(*)::int
                FROM workflow_run
                WHERE status = 'completed'
                  AND started_at >= NOW() - INTERVAL '5 minutes'
                """);
        Integer failedRuns5m = safeQueryInt("""
                SELECT COUNT(*)::int
                FROM workflow_run
                WHERE status = 'failed'
                  AND started_at >= NOW() - INTERVAL '5 minutes'
                """);
        List<FlinkHotSymbolResponse> hotSymbols = safeQueryHotSymbols("""
                SELECT symbol, COUNT(*)::int AS order_count
                FROM trade_order
                WHERE created_at >= NOW() - INTERVAL '15 minutes'
                GROUP BY symbol
                ORDER BY order_count DESC, symbol ASC
                LIMIT 6
                """);

        return new FlinkComputeMetricsResponse(
                flinkEnabled,
                engineMode == null || engineMode.isBlank() ? "embedded" : engineMode,
                jobName == null || jobName.isBlank() ? "aitradex-flink-compute" : jobName,
                sourceEvents1m,
                sourceEvents5m,
                processedEvents1m,
                processedEvents5m,
                orderFillRate5m,
                riskRejectRate5m,
                avgWorkflowLatencyMs5m,
                p95WorkflowLatencyMs5m,
                watermarkDelayMs,
                queuedOrdersNow,
                activeRunsNow,
                completedRuns5m,
                failedRuns5m,
                hotSymbols,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private OrderSummaryResponse toOrderSummary(Map<String, Object> row) {
        return new OrderSummaryResponse(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("symbol")),
                String.valueOf(row.get("side")),
                ((Number) row.get("quantity")).intValue(),
                String.valueOf(row.get("status")),
                (java.time.OffsetDateTime) row.get("created_at"));
    }

    private Integer safeQueryInt(String sql) {
        try {
            Number value = jdbcTemplate.queryForObject(sql, Number.class);
            return value == null ? 0 : value.intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private Long safeQueryLong(String sql) {
        try {
            Number value = jdbcTemplate.queryForObject(sql, Number.class);
            return value == null ? 0L : value.longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private BigDecimal safeQueryDecimal(String sql) {
        try {
            BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
            return value == null ? BigDecimal.ZERO : value;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private List<FlinkHotSymbolResponse> safeQueryHotSymbols(String sql) {
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> new FlinkHotSymbolResponse(
                    rs.getString("symbol"),
                    rs.getInt("order_count")));
        } catch (Exception e) {
            return List.of();
        }
    }
}
