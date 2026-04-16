package com.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BacktestRepository {
    private final JdbcTemplate jdbcTemplate;

    public BacktestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertBacktestReport(String strategyName, String symbol, String timeframe, int shortWindow, int longWindow,
                                     BigDecimal initialCash, int trades, BigDecimal winRate, BigDecimal totalReturn,
                                     BigDecimal maxDrawdown, BigDecimal finalEquity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO backtest_report (
                      strategy_name, symbol, timeframe, short_window, long_window, initial_cash,
                      trades, win_rate, total_return, max_drawdown, final_equity
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, strategyName);
            ps.setString(2, symbol);
            ps.setString(3, timeframe);
            ps.setInt(4, shortWindow);
            ps.setInt(5, longWindow);
            ps.setBigDecimal(6, initialCash);
            ps.setInt(7, trades);
            ps.setBigDecimal(8, winRate);
            ps.setBigDecimal(9, totalReturn);
            ps.setBigDecimal(10, maxDrawdown);
            ps.setBigDecimal(11, finalEquity);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public List<java.util.Map<String, Object>> listBacktestReports(int limit) {
        return jdbcTemplate.queryForList("""
                SELECT id, strategy_name, symbol, timeframe, short_window, long_window, initial_cash,
                       trades, win_rate, total_return, max_drawdown, final_equity, created_at
                FROM backtest_report
                ORDER BY created_at DESC
                LIMIT ?
                """, limit);
    }
}
