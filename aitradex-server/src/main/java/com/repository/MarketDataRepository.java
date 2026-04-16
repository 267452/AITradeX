package com.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MarketDataRepository {
    private final JdbcTemplate jdbcTemplate;

    public MarketDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getLatestBar(String symbol, String timeframe) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT symbol, timeframe, bar_time, open, high, low, close, volume
                FROM market_bar
                WHERE symbol = ? AND timeframe = ?
                ORDER BY bar_time DESC
                LIMIT 1
                """, symbol, timeframe);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<BigDecimal> getRecentCloses(String symbol, String timeframe, int limit) {
        List<BigDecimal> closes = jdbcTemplate.query("""
                SELECT close FROM market_bar
                WHERE symbol = ? AND timeframe = ?
                ORDER BY bar_time DESC
                LIMIT ?
                """, (rs, rowNum) -> rs.getBigDecimal(1), symbol, timeframe, limit);
        Collections.reverse(closes);
        return closes;
    }

    public List<Map<String, Object>> getRecentBars(String symbol, String timeframe, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT bar_time, open, high, low, close, volume
                FROM market_bar
                WHERE symbol = ? AND timeframe = ?
                ORDER BY bar_time ASC
                LIMIT ?
                """, symbol, timeframe, limit);
    }

    public boolean insertMarketBar(String symbol, String timeframe, OffsetDateTime barTime, BigDecimal open,
                                   BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
        Integer inserted = jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO market_bar (symbol, timeframe, bar_time, open, high, low, close, volume)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (symbol, timeframe, bar_time) DO NOTHING
                    RETURNING 1
                    """);
            ps.setString(1, symbol);
            ps.setString(2, timeframe);
            ps.setObject(3, barTime);
            ps.setBigDecimal(4, open);
            ps.setBigDecimal(5, high);
            ps.setBigDecimal(6, low);
            ps.setBigDecimal(7, close);
            ps.setBigDecimal(8, volume);
            return ps;
        }, rs -> rs.next() ? rs.getInt(1) : null);
        return inserted != null && inserted == 1;
    }
}
