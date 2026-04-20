package com.service;

import com.config.AppProperties;
import com.domain.entity.RiskRuleEntity;
import com.domain.request.ExecutionContext;
import com.domain.request.SignalRequest;
import com.domain.response.RiskCheckResult;
import com.repository.RiskRepository;
import com.repository.TradeRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskService {
    private static final Logger logger = LoggerFactory.getLogger(RiskService.class);

    private final AppProperties properties;
    private final TradeRepository tradeRepository;
    private final RiskRepository riskRepository;
    private final Map<String, OffsetDateTime> lastTradeTimeMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> tradeCountMap = new ConcurrentHashMap<>();

    public RiskService(AppProperties properties, TradeRepository tradeRepository, RiskRepository riskRepository) {
        this.properties = properties;
        this.tradeRepository = tradeRepository;
        this.riskRepository = riskRepository;
    }

    public RiskCheckResult checkRisk(SignalRequest signal) {
        return checkRisk(signal, null, true);
    }

    public RiskCheckResult checkRisk(SignalRequest signal, ExecutionContext executionContext) {
        return checkRisk(signal, executionContext, true);
    }

    public RiskCheckResult previewRisk(SignalRequest signal) {
        return checkRisk(signal, null, false);
    }

    public RiskCheckResult previewRisk(SignalRequest signal, ExecutionContext executionContext) {
        return checkRisk(signal, executionContext, false);
    }

    public RiskCheckResult checkRisk(SignalRequest signal, ExecutionContext executionContext, boolean mutateRuntimeState) {
        EffectiveRiskConfig riskConfig = resolveRiskConfig();
        logger.info("Performing risk check for signal: strategy={}, symbol={}, side={}", 
                    signal.strategyName(), signal.symbol(), signal.side());

        if (!riskConfig.enabled()) {
            logger.info("Risk check skipped: risk is disabled");
            return new RiskCheckResult(true, "risk_disabled");
        }

        RiskCheckResult basicCheck = checkBasicRisk(signal, riskConfig);
        if (!basicCheck.passed()) {
            return basicCheck;
        }

        RiskCheckResult priceCheck = checkPriceVolatility(signal, riskConfig);
        if (!priceCheck.passed()) {
            return priceCheck;
        }

        RiskCheckResult frequencyCheck = checkTradeFrequency(signal, riskConfig, mutateRuntimeState);
        if (!frequencyCheck.passed()) {
            return frequencyCheck;
        }

        RiskCheckResult positionCheck = checkPositionLimit(signal, riskConfig);
        if (!positionCheck.passed()) {
            return positionCheck;
        }

        RiskCheckResult strategyCheck = checkStrategyLimit(signal, riskConfig);
        if (!strategyCheck.passed()) {
            return strategyCheck;
        }

        logger.info("All risk checks passed for signal: symbol={}", signal.symbol());
        return new RiskCheckResult(true, "all_risk_checks_passed");
    }

    public Map<String, Object> getEffectiveRiskConfigSnapshot() {
        EffectiveRiskConfig config = resolveRiskConfig();
        Map<String, Object> snapshot = new ConcurrentHashMap<>();
        snapshot.put("enabled", config.enabled());
        snapshot.put("max_qty", config.maxQty());
        snapshot.put("max_notional", config.maxNotional());
        snapshot.put("allow_short", config.allowShort());
        snapshot.put("trade_frequency_limit_sec", config.tradeFrequencyLimitSec());
        snapshot.put("daily_trade_limit", config.dailyTradeLimit());
        snapshot.put("price_volatility_threshold", config.priceVolatilityThreshold());
        snapshot.put("max_position_per_symbol", config.maxPositionPerSymbol());
        snapshot.put("max_strategy_notional", config.maxStrategyNotional());
        return snapshot;
    }

    private EffectiveRiskConfig resolveRiskConfig() {
        EffectiveRiskConfig fallback = new EffectiveRiskConfig(
                properties.isRiskEnabled(),
                properties.getRiskMaxQty(),
                properties.getRiskMaxNotional(),
                properties.isRiskAllowShort(),
                properties.getRiskTradeFrequencyLimitSec(),
                properties.getRiskDailyTradeLimit(),
                properties.getRiskPriceVolatilityThreshold(),
                properties.getRiskMaxPositionPerSymbol(),
                properties.getRiskMaxStrategyNotional());

        List<RiskRuleEntity> enabledRules;
        try {
            enabledRules = riskRepository.getEnabledRiskRules();
        } catch (Exception e) {
            logger.warn("Failed to load risk rules from DB, fallback to app properties: {}", e.getMessage());
            return fallback;
        }
        if (enabledRules.isEmpty()) {
            return fallback;
        }

        int maxQty = fallback.maxQty();
        BigDecimal maxNotional = fallback.maxNotional();
        boolean allowShort = fallback.allowShort();
        int tradeFrequencyLimitSec = fallback.tradeFrequencyLimitSec();
        int dailyTradeLimit = fallback.dailyTradeLimit();
        BigDecimal priceVolatilityThreshold = fallback.priceVolatilityThreshold();
        int maxPositionPerSymbol = fallback.maxPositionPerSymbol();
        BigDecimal maxStrategyNotional = fallback.maxStrategyNotional();

        for (RiskRuleEntity rule : enabledRules) {
            Map<String, Object> config = rule.ruleConfig() == null ? Map.of() : rule.ruleConfig();
            switch (rule.ruleType()) {
                case "max_quantity" -> maxQty = asInt(config, "limit", maxQty);
                case "max_notional" -> maxNotional = asBigDecimal(config, "limit", maxNotional);
                case "short_selling" -> allowShort = asBoolean(config, "allow", allowShort);
                case "trade_frequency" -> tradeFrequencyLimitSec = asInt(config, "limit_seconds", tradeFrequencyLimitSec);
                case "daily_trade_limit" -> dailyTradeLimit = asInt(config, "limit", dailyTradeLimit);
                case "price_volatility" -> priceVolatilityThreshold = asBigDecimal(config, "threshold", priceVolatilityThreshold);
                case "max_position" -> maxPositionPerSymbol = asInt(config, "limit_per_symbol", maxPositionPerSymbol);
                case "strategy_notional" -> maxStrategyNotional = asBigDecimal(config, "limit", maxStrategyNotional);
                default -> logger.debug("Unsupported risk rule type {}, ignored", rule.ruleType());
            }
        }

        return new EffectiveRiskConfig(
                fallback.enabled(),
                maxQty,
                maxNotional,
                allowShort,
                tradeFrequencyLimitSec,
                dailyTradeLimit,
                priceVolatilityThreshold,
                maxPositionPerSymbol,
                maxStrategyNotional);
    }

    private RiskCheckResult checkBasicRisk(SignalRequest signal, EffectiveRiskConfig riskConfig) {
        if (signal.quantity() > riskConfig.maxQty()) {
            logger.warn("Risk check failed: quantity {} exceeds limit {}",
                    signal.quantity(), riskConfig.maxQty());
            return new RiskCheckResult(false, "quantity_exceeds_limit");
        }

        BigDecimal notional = safePrice(signal).multiply(BigDecimal.valueOf(signal.quantity()));
        if (notional.compareTo(riskConfig.maxNotional()) > 0) {
            logger.warn("Risk check failed: notional {} exceeds limit {}",
                    notional, riskConfig.maxNotional());
            return new RiskCheckResult(false, "notional_exceeds_limit");
        }

        if ("sell".equals(signal.side()) && !riskConfig.allowShort()) {
            logger.warn("Risk check failed: short selling not allowed for symbol {}", signal.symbol());
            return new RiskCheckResult(false, "short_not_allowed");
        }

        return new RiskCheckResult(true, "basic_check_passed");
    }

    private RiskCheckResult checkPriceVolatility(SignalRequest signal, EffectiveRiskConfig riskConfig) {
        if (signal.price() == null || signal.price().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Risk check failed: invalid price {}", signal.price());
            return new RiskCheckResult(false, "invalid_price");
        }

        BigDecimal threshold = riskConfig.priceVolatilityThreshold();
        if (threshold != null
                && threshold.compareTo(BigDecimal.ZERO) > 0
                && signal.signalStrength() != null
                && signal.signalStrength().abs().compareTo(threshold) > 0) {
            logger.warn("Risk check failed: signal_strength {} exceeds volatility threshold {}",
                    signal.signalStrength(), threshold);
            return new RiskCheckResult(false, "price_volatility_exceeds_threshold");
        }

        return new RiskCheckResult(true, "price_check_passed");
    }

    private RiskCheckResult checkTradeFrequency(SignalRequest signal, EffectiveRiskConfig riskConfig,
                                                boolean mutateRuntimeState) {
        String key = signal.symbol() + ":" + signal.strategyName();
        OffsetDateTime lastTradeTime = lastTradeTimeMap.get(key);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (lastTradeTime != null) {
            Duration duration = Duration.between(lastTradeTime, now);
            if (riskConfig.tradeFrequencyLimitSec() > 0
                    && duration.toSeconds() < riskConfig.tradeFrequencyLimitSec()) {
                logger.warn("Risk check failed: trade frequency too high for {}", key);
                return new RiskCheckResult(false, "trade_frequency_too_high");
            }
        }

        Integer count = tradeCountMap.getOrDefault(key, 0);
        if (riskConfig.dailyTradeLimit() > 0 && count >= riskConfig.dailyTradeLimit()) {
            logger.warn("Risk check failed: daily trade limit reached for {}", key);
            return new RiskCheckResult(false, "daily_trade_limit_reached");
        }

        if (mutateRuntimeState) {
            lastTradeTimeMap.put(key, now);
            tradeCountMap.put(key, count + 1);
        }

        return new RiskCheckResult(true, "frequency_check_passed");
    }

    private RiskCheckResult checkPositionLimit(SignalRequest signal, EffectiveRiskConfig riskConfig) {
        if (riskConfig.maxPositionPerSymbol() <= 0) {
            return new RiskCheckResult(true, "position_check_passed");
        }

        int currentPosition = tradeRepository.getLatestPositionQuantity(signal.symbol());
        int projectedPosition = "buy".equals(signal.side())
                ? currentPosition + signal.quantity()
                : currentPosition - signal.quantity();
        if (Math.abs(projectedPosition) > riskConfig.maxPositionPerSymbol()) {
            logger.warn("Risk check failed: projected position {} exceeds limit {} for symbol {}",
                    projectedPosition, riskConfig.maxPositionPerSymbol(), signal.symbol());
            return new RiskCheckResult(false, "position_limit_exceeded");
        }

        return new RiskCheckResult(true, "position_check_passed");
    }

    private RiskCheckResult checkStrategyLimit(SignalRequest signal, EffectiveRiskConfig riskConfig) {
        if (riskConfig.maxStrategyNotional() == null
                || riskConfig.maxStrategyNotional().compareTo(BigDecimal.ZERO) <= 0) {
            return new RiskCheckResult(true, "strategy_check_passed");
        }

        BigDecimal todayNotional = tradeRepository.getTodayStrategyNotional(signal.strategyName());
        BigDecimal currentNotional = safePrice(signal).multiply(BigDecimal.valueOf(signal.quantity()));
        BigDecimal projected = todayNotional.add(currentNotional);

        if (projected.compareTo(riskConfig.maxStrategyNotional()) > 0) {
            logger.warn("Risk check failed: projected strategy notional {} exceeds limit {} for strategy {}",
                    projected, riskConfig.maxStrategyNotional(), signal.strategyName());
            return new RiskCheckResult(false, "strategy_notional_exceeded");
        }

        return new RiskCheckResult(true, "strategy_check_passed");
    }

    public void logRiskCheck(String checkName, boolean passed, String reason, Map<String, Object> details) {
        logRiskCheck(checkName, passed, reason, details, null);
    }

    public void logRiskCheck(String checkName, boolean passed, String reason, Map<String, Object> details,
                             ExecutionContext executionContext) {
        tradeRepository.insertRiskLog(checkName, passed, reason, details, executionContext);
    }

    public void resetDailyTradeCounts() {
        tradeCountMap.clear();
        logger.info("Daily trade counts reset");
    }

    private BigDecimal safePrice(SignalRequest signal) {
        return signal.price() == null ? BigDecimal.ZERO : signal.price();
    }

    private int asInt(Map<String, Object> config, String key, int defaultValue) {
        Object raw = config.get(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BigDecimal asBigDecimal(Map<String, Object> config, String key, BigDecimal defaultValue) {
        Object raw = config.get(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean asBoolean(Map<String, Object> config, String key, boolean defaultValue) {
        Object raw = config.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private record EffectiveRiskConfig(
            boolean enabled,
            int maxQty,
            BigDecimal maxNotional,
            boolean allowShort,
            int tradeFrequencyLimitSec,
            int dailyTradeLimit,
            BigDecimal priceVolatilityThreshold,
            int maxPositionPerSymbol,
            BigDecimal maxStrategyNotional) {}
}
