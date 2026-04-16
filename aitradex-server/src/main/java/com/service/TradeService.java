package com.service;

import com.config.AppProperties;
import com.domain.request.SignalRequest;
import com.domain.request.StrategyRunRequest;
import com.domain.request.TradeCommandRequest;
import com.domain.response.SignalOrderIds;
import com.domain.response.RiskCheckResult;
import com.domain.response.SignalProcessResponse;
import com.domain.response.StrategySignalResult;
import com.domain.response.TradeCommandParseResult;
import com.repository.MarketDataRepository;
import com.repository.TradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TradeService {
    private static final Logger logger = LoggerFactory.getLogger(TradeService.class);
    
    private final TradeRepository tradeRepository;
    private final MarketDataRepository marketDataRepository;
    private final AppProperties properties;
    private final BrokerService brokerService;

    public TradeService(TradeRepository tradeRepository, MarketDataRepository marketDataRepository,
                        AppProperties properties, BrokerService brokerService) {
        this.tradeRepository = tradeRepository;
        this.marketDataRepository = marketDataRepository;
        this.properties = properties;
        this.brokerService = brokerService;
    }

    public SignalProcessResponse processSignal(SignalRequest signal) {
        logger.info("Processing signal: strategy={}, symbol={}, side={}, qty={}, price={}", 
                    signal.strategyName(), signal.symbol(), signal.side(), signal.quantity(), signal.price());
        
        RiskCheckResult risk = runRisk(signal);
        logger.info("Risk check result: passed={}, reason={}", risk.passed(), risk.reason());
        
        tradeRepository.insertRiskLog("basic_pre_trade_check", risk.passed(), risk.reason(), java.util.Map.of(
                "strategy_name", signal.strategyName(),
                "symbol", signal.symbol(),
                "side", signal.side(),
                "quantity", signal.quantity()));
        
        if (!risk.passed()) {
            logger.warn("Signal rejected by risk check: symbol={}, reason={}", signal.symbol(), risk.reason());
            return new SignalProcessResponse(0L, 0L, false, "risk_rejected: " + risk.reason());
        }
        
        SignalOrderIds ids = tradeRepository.createSignalAndOrder(
                signal.strategyName(),
                signal.symbol(),
                signal.side(),
                signal.signalStrength(),
                signal.signalTime() == null ? OffsetDateTime.now(ZoneOffset.UTC) : signal.signalTime(),
                signal.price(),
                signal.quantity());
        
        long orderId = ids.orderId();
        logger.info("Signal created successfully: signalId={}, orderId={}, symbol={}", 
                    ids.signalId(), orderId, signal.symbol());
        
        brokerService.executeOrderViaBroker(orderId);
        logger.info("Order queued for execution: orderId={}", orderId);
        
        return new SignalProcessResponse(ids.signalId(), orderId, true, "signal accepted and order queued");
    }

    public RiskCheckResult runRisk(SignalRequest signal) {
        if (signal.quantity() > properties.getRiskMaxQty()) {
            logger.warn("Risk check failed: quantity {} exceeds limit {}", signal.quantity(), properties.getRiskMaxQty());
            return new RiskCheckResult(false, "quantity_exceeds_limit");
        }
        BigDecimal notional = signal.price().multiply(BigDecimal.valueOf(signal.quantity()));
        if (notional.compareTo(properties.getRiskMaxNotional()) > 0) {
            logger.warn("Risk check failed: notional {} exceeds limit {}", notional, properties.getRiskMaxNotional());
            return new RiskCheckResult(false, "notional_exceeds_limit");
        }
        if ("sell".equals(signal.side()) && !properties.isRiskAllowShort() && signal.signalStrength().compareTo(BigDecimal.ZERO) >= 0) {
            logger.warn("Risk check failed: short selling not allowed for symbol {}", signal.symbol());
            return new RiskCheckResult(false, "short_not_allowed");
        }
        logger.debug("Risk check passed for symbol {}", signal.symbol());
        return new RiskCheckResult(true, "passed");
    }

    public TradeCommandParseResult parseTradeCommand(TradeCommandRequest req) {
        logger.debug("Parsing trade command: {}", req.command());
        
        String cmd = req.command().trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(买入|卖出)\\s*([0-9A-Za-z\\.\\-]+)\\s*(\\d+)").matcher(cmd);
        if (m.find()) {
            logger.info("Trade command parsed as manual signal: {} {} {} shares", 
                        m.group(1), m.group(2), m.group(3));
            return new TradeCommandParseResult(
                    true,
                    "manual_signal",
                    "parsed",
                    normalizeSymbol(m.group(2)),
                    "买入".equals(m.group(1)) ? "buy" : "sell",
                    Integer.parseInt(m.group(3)),
                    "manual_command");
        }
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(?:运行策略|策略)\\s*(sma|均线)?\\s*([0-9A-Za-z\\.\\-]+)\\s*(\\d+)?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cmd);
        if (m2.find()) {
            logger.info("Trade command parsed as strategy run: {}", m2.group(2));
            return new TradeCommandParseResult(
                    true,
                    "strategy_run",
                    "parsed",
                    normalizeSymbol(m2.group(2)),
                    null,
                    m2.group(3) == null || m2.group(3).isBlank() ? 100 : Integer.parseInt(m2.group(3)),
                    "sma_cmd_v1");
        }
        logger.warn("Failed to parse trade command: {}", req.command());
        return new TradeCommandParseResult(false, "unknown", "无法识别指令，请用：买入/卖出 代码 数量 或 运行策略 代码 数量", null, null, null, null);
    }

    public SignalRequest buildManualSignal(BigDecimal price, String side, String symbol, int qty, String strategyName) {
        BigDecimal strength = "buy".equals(side) ? new BigDecimal("0.2") : new BigDecimal("-0.2");
        return new SignalRequest(strategyName, symbol, side, strength, price, qty, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public StrategyRunRequest buildStrategyRequest(String symbol, int qty, String strategyName) {
        return new StrategyRunRequest(strategyName, symbol, "1d", 5, 20, qty);
    }

    public String normalizeSymbol(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.endsWith(".US") || s.contains(".")) return s;
        if (s.matches("\\d{6}")) {
            if (s.startsWith("8") || s.startsWith("4") || s.startsWith("9")) return s + ".BJ";
            return s.startsWith("6") ? s + ".SH" : s + ".SZ";
        }
        return s;
    }

    public StrategySignalResult generateSignalFromSma(StrategyRunRequest req) {
        logger.info("Generating SMA signal: symbol={}, shortWindow={}, longWindow={}, qty={}", 
                    req.symbol(), req.shortWindow(), req.longWindow(), req.quantity());
        
        if (req.shortWindow() >= req.longWindow()) {
            logger.error("Invalid SMA parameters: shortWindow must be less than longWindow");
            return new StrategySignalResult(null, "short_window_must_be_less_than_long_window");
        }
        int needed = req.longWindow() + 1;
        var closes = marketDataRepository.getRecentCloses(req.symbol(), req.timeframe(), needed);
        if (closes.size() < needed) {
            logger.warn("Insufficient market data: need {}, have {}", needed, closes.size());
            return new StrategySignalResult(null, "insufficient_bars_need_" + needed + "_have_" + closes.size());
        }
        var prev = closes.subList(0, closes.size() - 1);
        BigDecimal prevShort = sma(prev.subList(prev.size() - req.shortWindow(), prev.size()));
        BigDecimal prevLong = sma(prev.subList(prev.size() - req.longWindow(), prev.size()));
        BigDecimal currShort = sma(closes.subList(closes.size() - req.shortWindow(), closes.size()));
        BigDecimal currLong = sma(closes.subList(closes.size() - req.longWindow(), closes.size()));
        String side = null;
        BigDecimal strength = BigDecimal.ZERO;
        if (prevShort.compareTo(prevLong) <= 0 && currShort.compareTo(currLong) > 0) {
            side = "buy";
            strength = currShort.subtract(currLong).divide(currLong, 8, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        } else if (prevShort.compareTo(prevLong) >= 0 && currShort.compareTo(currLong) < 0) {
            side = "sell";
            strength = currShort.subtract(currLong).divide(currLong, 8, RoundingMode.HALF_UP).max(BigDecimal.ONE.negate());
        } else {
            BigDecimal gap = currShort.subtract(currLong).divide(currLong, 8, RoundingMode.HALF_UP);
            if (gap.compareTo(new BigDecimal("0.01")) >= 0) {
                side = "buy";
                strength = gap.min(BigDecimal.ONE);
            } else if (gap.compareTo(new BigDecimal("-0.01")) <= 0) {
                side = "sell";
                strength = gap.max(BigDecimal.ONE.negate());
            } else {
                logger.info("No crossover signal for symbol {}", req.symbol());
                return new StrategySignalResult(null, "no_cross_signal");
            }
        }
        String latestSide = tradeRepository.getLatestSignalSide(req.strategyName(), req.symbol());
        if (side.equals(latestSide)) {
            logger.info("Duplicate signal detected, skipping: strategy={}, symbol={}, side={}", 
                        req.strategyName(), req.symbol(), side);
            return new StrategySignalResult(null, "duplicate_side_signal_skipped");
        }
        logger.info("SMA signal generated: symbol={}, side={}, strength={}", req.symbol(), side, strength);
        return new StrategySignalResult(
                new SignalRequest(req.strategyName(), req.symbol(), side, strength, closes.get(closes.size() - 1), req.quantity(), OffsetDateTime.now(ZoneOffset.UTC)),
                "ok");
    }

    private BigDecimal sma(java.util.List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
    }
}
