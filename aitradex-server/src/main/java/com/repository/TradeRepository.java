package com.repository;

import com.domain.entity.OrderEntity;
import com.domain.request.ExecutionContext;
import com.domain.response.OrderExecutionResult;
import com.domain.response.SignalOrderIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TradeRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TradeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public OrderEntity getOrderById(long orderId) {
        List<OrderEntity> rows = jdbcTemplate.query("""
                SELECT id, symbol, side, order_type, price, quantity, status, strategy_name
                FROM trade_order
                WHERE id = ?
                """, (rs, rowNum) -> mapOrder(rs), orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String getLatestSignalSide(String strategyName, String symbol) {
        List<String> rows = jdbcTemplate.query("""
                SELECT side FROM strategy_signal
                WHERE strategy_name = ? AND symbol = ?
                ORDER BY signal_time DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), strategyName, symbol);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public SignalOrderIds createSignalAndOrder(String strategyName, String symbol, String side, BigDecimal signalStrength,
                                               OffsetDateTime signalTime, BigDecimal price, int quantity,
                                               ExecutionContext executionContext) {
        KeyHolder signalKey = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO strategy_signal (
                        strategy_name, symbol, side, signal_strength, signal_time,
                        run_id, conversation_id, workflow_id, workflow_run_id
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, strategyName);
            ps.setString(2, symbol);
            ps.setString(3, side);
            ps.setBigDecimal(4, signalStrength);
            ps.setObject(5, signalTime);
            ps.setString(6, executionContext != null ? executionContext.runId() : null);
            ps.setObject(7, executionContext != null ? executionContext.conversationId() : null);
            ps.setObject(8, executionContext != null ? executionContext.workflowId() : null);
            ps.setObject(9, executionContext != null ? executionContext.workflowRunId() : null);
            return ps;
        }, signalKey);
        KeyHolder orderKey = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO trade_order (
                        symbol, side, order_type, price, quantity, status, strategy_name,
                        run_id, conversation_id, workflow_id, workflow_run_id
                    )
                    VALUES (?, ?, 'market', ?, ?, 'queued', ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, symbol);
            ps.setString(2, side);
            ps.setBigDecimal(3, price);
            ps.setInt(4, quantity);
            ps.setString(5, strategyName);
            ps.setString(6, executionContext != null ? executionContext.runId() : null);
            ps.setObject(7, executionContext != null ? executionContext.conversationId() : null);
            ps.setObject(8, executionContext != null ? executionContext.workflowId() : null);
            ps.setObject(9, executionContext != null ? executionContext.workflowRunId() : null);
            return ps;
        }, orderKey);
        return new SignalOrderIds(signalKey.getKey().longValue(), orderKey.getKey().longValue());
    }

    public int getLatestPositionQuantity(String symbol) {
        List<Integer> rows = jdbcTemplate.query("""
                SELECT quantity
                FROM position_snapshot
                WHERE symbol = ?
                ORDER BY snapshot_time DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getInt(1), symbol);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    public BigDecimal getTodayStrategyNotional(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> rows = jdbcTemplate.query("""
                SELECT COALESCE(SUM(ABS(COALESCE(price, 0) * quantity)), 0)
                FROM trade_order
                WHERE strategy_name = ?
                  AND created_at >= date_trunc('day', NOW())
                """, (rs, rowNum) -> rs.getBigDecimal(1), strategyName);
        if (rows.isEmpty() || rows.get(0) == null) {
            return BigDecimal.ZERO;
        }
        return rows.get(0);
    }

    public void insertRiskLog(String checkName, boolean passed, String reason,
                              Map<String, Object> context, ExecutionContext executionContext) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO risk_check_log (
                        check_name, passed, reason, context,
                        run_id, conversation_id, workflow_id, workflow_run_id
                    )
                    VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    """,
                    checkName, passed, reason, objectMapper.writeValueAsString(context),
                    executionContext != null ? executionContext.runId() : null,
                    executionContext != null ? executionContext.conversationId() : null,
                    executionContext != null ? executionContext.workflowId() : null,
                    executionContext != null ? executionContext.workflowRunId() : null);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json_encode_failed", e);
        }
    }

    public boolean markOrderSubmitted(long orderId, String brokerOrderId) {
        Integer updated = jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    UPDATE trade_order
                    SET status = 'submitted', broker_order_id = ?, updated_at = NOW()
                    WHERE id = ? AND status = 'queued'
                    RETURNING 1
                    """);
            ps.setString(1, brokerOrderId);
            ps.setLong(2, orderId);
            return ps;
        }, rs -> rs.next() ? rs.getInt(1) : null);
        return updated != null && updated == 1;
    }

    @Transactional
    public OrderExecutionResult executeOrderFill(long orderId, BigDecimal fillPriceOverride, String brokerOrderId) {
        List<OrderEntity> orderRows = jdbcTemplate.query("""
                SELECT id, symbol, side, price, quantity, status
                FROM trade_order WHERE id = ? FOR UPDATE
                """, (rs, rowNum) -> new OrderEntity(
                rs.getLong("id"),
                rs.getString("symbol"),
                rs.getString("side"),
                null,
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getString("status"),
                null
        ), orderId);
        if (orderRows.isEmpty()) {
            return new OrderExecutionResult(false, "order_not_found", orderId, null);
        }
        OrderEntity order = orderRows.get(0);
        String status = order.status();
        if (!List.of("queued", "submitted").contains(status)) {
            return new OrderExecutionResult(false, "already_" + status, orderId, status);
        }
        OffsetDateTime fillTime = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal fillPrice = fillPriceOverride != null ? fillPriceOverride : order.price();
        if (fillPrice == null) {
            fillPrice = BigDecimal.ZERO;
        }
        int qty = order.quantity();
        int qtyDelta = "buy".equals(order.side()) ? qty : -qty;

        jdbcTemplate.update("""
                UPDATE trade_order
                SET status = 'filled', broker_order_id = COALESCE(?, broker_order_id), updated_at = NOW()
                WHERE id = ?
                """, brokerOrderId, orderId);
        jdbcTemplate.update("""
                INSERT INTO trade_fill (order_id, fill_price, fill_quantity, fill_time)
                VALUES (?, ?, ?, ?)
                """, orderId, fillPrice, qty, fillTime);

        List<Map<String, Object>> prevRows = jdbcTemplate.queryForList("""
                SELECT quantity, avg_cost FROM position_snapshot
                WHERE symbol = ?
                ORDER BY snapshot_time DESC
                LIMIT 1
                """, order.symbol());
        int prevQty = prevRows.isEmpty() ? 0 : ((Number) prevRows.get(0).get("quantity")).intValue();
        BigDecimal prevCost = prevRows.isEmpty() ? BigDecimal.ZERO : (BigDecimal) prevRows.get(0).get("avg_cost");
        int newQty = prevQty + qtyDelta;
        BigDecimal newCost;
        if (newQty == 0) {
            newCost = BigDecimal.ZERO;
        } else if (qtyDelta > 0) {
            newCost = prevCost.multiply(BigDecimal.valueOf(prevQty)).add(fillPrice.multiply(BigDecimal.valueOf(qtyDelta)))
                    .divide(BigDecimal.valueOf(newQty), 8, java.math.RoundingMode.HALF_UP);
        } else {
            newCost = prevCost;
        }
        jdbcTemplate.update("""
                INSERT INTO position_snapshot (symbol, quantity, avg_cost, snapshot_time)
                VALUES (?, ?, ?, ?)
                """, order.symbol(), newQty, newCost, fillTime);

        List<Map<String, Object>> eqRows = jdbcTemplate.queryForList("""
                SELECT cash_balance, market_value, total_equity
                FROM account_snapshot ORDER BY snapshot_time DESC LIMIT 1
                """);
        BigDecimal cash = eqRows.isEmpty() ? new BigDecimal("1000000") : (BigDecimal) eqRows.get(0).get("cash_balance");
        BigDecimal marketValue = eqRows.isEmpty() ? BigDecimal.ZERO : (BigDecimal) eqRows.get(0).get("market_value");
        BigDecimal tradeNotional = fillPrice.multiply(BigDecimal.valueOf(qty));
        if ("buy".equals(order.side())) {
            cash = cash.subtract(tradeNotional);
            marketValue = marketValue.add(tradeNotional);
        } else {
            cash = cash.add(tradeNotional);
            marketValue = marketValue.subtract(tradeNotional);
        }
        BigDecimal totalEquity = cash.add(marketValue);
        jdbcTemplate.update("""
                INSERT INTO account_snapshot (cash_balance, market_value, total_equity, snapshot_time)
                VALUES (?, ?, ?, ?)
                """, cash, marketValue, totalEquity, fillTime);
        return new OrderExecutionResult(true, null, orderId, "filled");
    }

    private OrderEntity mapOrder(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderEntity(
                rs.getLong("id"),
                rs.getString("symbol"),
                rs.getString("side"),
                rs.getString("order_type"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getString("status"),
                rs.getString("strategy_name"));
    }
}
