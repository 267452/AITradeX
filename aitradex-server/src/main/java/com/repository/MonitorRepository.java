package com.repository;

import com.domain.response.MonitorSummaryResponse;
import com.domain.response.OrderPageResponse;
import com.domain.response.OrderSummaryResponse;
import java.math.BigDecimal;
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

    private OrderSummaryResponse toOrderSummary(Map<String, Object> row) {
        return new OrderSummaryResponse(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("symbol")),
                String.valueOf(row.get("side")),
                ((Number) row.get("quantity")).intValue(),
                String.valueOf(row.get("status")),
                (java.time.OffsetDateTime) row.get("created_at"));
    }
}
