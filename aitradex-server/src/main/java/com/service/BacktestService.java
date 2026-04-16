package com.service;

import com.domain.request.BacktestRequest;
import com.repository.BacktestRepository;
import com.repository.MarketDataRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BacktestService {
    private final MarketDataRepository marketDataRepository;
    private final BacktestRepository backtestRepository;

    public BacktestService(MarketDataRepository marketDataRepository, BacktestRepository backtestRepository) {
        this.marketDataRepository = marketDataRepository;
        this.backtestRepository = backtestRepository;
    }

    public Map<String, Object> runSmaBacktest(BacktestRequest req) {
        List<Map<String, Object>> bars = marketDataRepository.getRecentBars(req.symbol(), req.timeframe(), 20000);
        List<BigDecimal> closes = bars.stream().map(row -> (BigDecimal) row.get("close")).toList();
        if (closes.size() < req.longWindow() + 2) {
            long reportId = backtestRepository.insertBacktestReport(req.strategyName(), req.symbol(), req.timeframe(), req.shortWindow(), req.longWindow(), req.initialCash(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, req.initialCash());
            return Map.of("strategy_name", req.strategyName(), "symbol", req.symbol(), "trades", 0, "win_rate", BigDecimal.ZERO, "total_return", BigDecimal.ZERO, "max_drawdown", BigDecimal.ZERO, "final_equity", req.initialCash(), "report_id", reportId);
        }
        BigDecimal cash = req.initialCash();
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal entryPrice = null;
        java.util.List<BigDecimal> tradePnls = new java.util.ArrayList<>();
        java.util.List<BigDecimal> equityCurve = new java.util.ArrayList<>();
        for (int i = req.longWindow(); i < closes.size(); i++) {
            List<BigDecimal> prev = closes.subList(0, i);
            List<BigDecimal> curr = closes.subList(0, i + 1);
            BigDecimal prevShort = sma(prev.subList(prev.size() - req.shortWindow(), prev.size()));
            BigDecimal prevLong = sma(prev.subList(prev.size() - req.longWindow(), prev.size()));
            BigDecimal currShort = sma(curr.subList(curr.size() - req.shortWindow(), curr.size()));
            BigDecimal currLong = sma(curr.subList(curr.size() - req.longWindow(), curr.size()));
            BigDecimal px = curr.get(curr.size() - 1);
            if (qty.compareTo(BigDecimal.ZERO) == 0 && prevShort.compareTo(prevLong) <= 0 && currShort.compareTo(currLong) > 0) {
                qty = cash.divide(px, 8, java.math.RoundingMode.HALF_UP);
                cash = BigDecimal.ZERO;
                entryPrice = px;
            } else if (qty.compareTo(BigDecimal.ZERO) > 0 && prevShort.compareTo(prevLong) >= 0 && currShort.compareTo(currLong) < 0) {
                cash = qty.multiply(px);
                if (entryPrice != null) {
                    tradePnls.add(px.subtract(entryPrice).divide(entryPrice, 8, java.math.RoundingMode.HALF_UP));
                }
                qty = BigDecimal.ZERO;
                entryPrice = null;
            }
            equityCurve.add(cash.add(qty.multiply(px)));
        }
        if (qty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal px = closes.get(closes.size() - 1);
            cash = qty.multiply(px);
            if (entryPrice != null) {
                tradePnls.add(px.subtract(entryPrice).divide(entryPrice, 8, java.math.RoundingMode.HALF_UP));
            }
        }
        BigDecimal finalEquity = cash;
        BigDecimal totalReturn = finalEquity.subtract(req.initialCash()).divide(req.initialCash(), 8, java.math.RoundingMode.HALF_UP);
        long wins = tradePnls.stream().filter(p -> p.compareTo(BigDecimal.ZERO) > 0).count();
        int trades = tradePnls.size();
        BigDecimal winRate = trades == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(trades), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal peak = equityCurve.isEmpty() ? req.initialCash() : equityCurve.get(0);
        BigDecimal maxDd = BigDecimal.ZERO;
        for (BigDecimal eq : equityCurve) {
            if (eq.compareTo(peak) > 0) {
                peak = eq;
            }
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dd = peak.subtract(eq).divide(peak, 8, java.math.RoundingMode.HALF_UP);
                if (dd.compareTo(maxDd) > 0) {
                    maxDd = dd;
                }
            }
        }
        long reportId = backtestRepository.insertBacktestReport(req.strategyName(), req.symbol(), req.timeframe(), req.shortWindow(), req.longWindow(), req.initialCash(), trades, winRate, totalReturn, maxDd, finalEquity);
        return Map.of("strategy_name", req.strategyName(), "symbol", req.symbol(), "trades", trades, "win_rate", winRate, "total_return", totalReturn, "max_drawdown", maxDd, "final_equity", finalEquity, "report_id", reportId);
    }

    public List<Map<String, Object>> listReports(int limit) {
        return backtestRepository.listBacktestReports(limit);
    }

    private BigDecimal sma(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 8, java.math.RoundingMode.HALF_UP);
    }
}
