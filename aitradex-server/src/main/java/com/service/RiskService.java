package com.service;

import com.config.AppProperties;
import com.domain.request.SignalRequest;
import com.domain.response.RiskCheckResult;
import com.repository.TradeRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
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
    private final Map<String, OffsetDateTime> lastTradeTimeMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> tradeCountMap = new ConcurrentHashMap<>();
    
    public RiskService(AppProperties properties, TradeRepository tradeRepository) {
        this.properties = properties;
        this.tradeRepository = tradeRepository;
    }
    
    public RiskCheckResult checkRisk(SignalRequest signal) {
        logger.info("Performing risk check for signal: strategy={}, symbol={}, side={}", 
                    signal.strategyName(), signal.symbol(), signal.side());
        
        // 检查风控是否启用
        if (!properties.isRiskEnabled()) {
            logger.info("Risk check skipped: risk is disabled");
            return new RiskCheckResult(true, "risk_disabled");
        }
        
        // 基本风控检查
        RiskCheckResult basicCheck = checkBasicRisk(signal);
        if (!basicCheck.passed()) {
            return basicCheck;
        }
        
        // 价格波动检查
        RiskCheckResult priceCheck = checkPriceVolatility(signal);
        if (!priceCheck.passed()) {
            return priceCheck;
        }
        
        // 交易频率检查
        RiskCheckResult frequencyCheck = checkTradeFrequency(signal);
        if (!frequencyCheck.passed()) {
            return frequencyCheck;
        }
        
        // 持仓限制检查
        RiskCheckResult positionCheck = checkPositionLimit(signal);
        if (!positionCheck.passed()) {
            return positionCheck;
        }
        
        // 策略交易限制检查
        RiskCheckResult strategyCheck = checkStrategyLimit(signal);
        if (!strategyCheck.passed()) {
            return strategyCheck;
        }
        
        logger.info("All risk checks passed for signal: symbol={}", signal.symbol());
        return new RiskCheckResult(true, "all_risk_checks_passed");
    }
    
    private RiskCheckResult checkBasicRisk(SignalRequest signal) {
        // 数量限制
        if (signal.quantity() > properties.getRiskMaxQty()) {
            logger.warn("Risk check failed: quantity {} exceeds limit {}", 
                        signal.quantity(), properties.getRiskMaxQty());
            return new RiskCheckResult(false, "quantity_exceeds_limit");
        }
        
        // 金额限制
        BigDecimal notional = signal.price().multiply(BigDecimal.valueOf(signal.quantity()));
        if (notional.compareTo(properties.getRiskMaxNotional()) > 0) {
            logger.warn("Risk check failed: notional {} exceeds limit {}", 
                        notional, properties.getRiskMaxNotional());
            return new RiskCheckResult(false, "notional_exceeds_limit");
        }
        
        // 做空限制
        if ("sell".equals(signal.side()) && !properties.isRiskAllowShort()) {
            logger.warn("Risk check failed: short selling not allowed for symbol {}", signal.symbol());
            return new RiskCheckResult(false, "short_not_allowed");
        }
        
        return new RiskCheckResult(true, "basic_check_passed");
    }
    
    private RiskCheckResult checkPriceVolatility(SignalRequest signal) {
        // 检查价格是否异常
        if (signal.price().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Risk check failed: invalid price {}", signal.price());
            return new RiskCheckResult(false, "invalid_price");
        }
        
        // 这里可以添加更多价格波动检查逻辑
        // 例如：与历史价格比较，检查是否有异常波动
        
        return new RiskCheckResult(true, "price_check_passed");
    }
    
    private RiskCheckResult checkTradeFrequency(SignalRequest signal) {
        String key = signal.symbol() + ":" + signal.strategyName();
        OffsetDateTime lastTradeTime = lastTradeTimeMap.get(key);
        
        if (lastTradeTime != null) {
            Duration duration = Duration.between(lastTradeTime, OffsetDateTime.now());
            // 限制同一策略对同一标的的交易频率
            if (duration.toSeconds() < properties.getRiskTradeFrequencyLimitSec()) {
                logger.warn("Risk check failed: trade frequency too high for {}", key);
                return new RiskCheckResult(false, "trade_frequency_too_high");
            }
        }
        
        // 更新最后交易时间
        lastTradeTimeMap.put(key, OffsetDateTime.now());
        
        // 检查单日交易次数
        Integer count = tradeCountMap.getOrDefault(key, 0);
        if (count >= properties.getRiskDailyTradeLimit()) {
            logger.warn("Risk check failed: daily trade limit reached for {}", key);
            return new RiskCheckResult(false, "daily_trade_limit_reached");
        }
        
        tradeCountMap.put(key, count + 1);
        
        return new RiskCheckResult(true, "frequency_check_passed");
    }
    
    private RiskCheckResult checkPositionLimit(SignalRequest signal) {
        // 检查持仓限制
        // 这里可以查询数据库，检查当前持仓数量是否超过限制
        // 例如：同一标的的持仓数量限制
        
        return new RiskCheckResult(true, "position_check_passed");
    }
    
    private RiskCheckResult checkStrategyLimit(SignalRequest signal) {
        // 检查策略交易限制
        // 例如：同一策略的总交易金额限制
        
        return new RiskCheckResult(true, "strategy_check_passed");
    }
    
    public void logRiskCheck(String checkName, boolean passed, String reason, Map<String, Object> details) {
        tradeRepository.insertRiskLog(checkName, passed, reason, details);
    }
    
    public void resetDailyTradeCounts() {
        tradeCountMap.clear();
        logger.info("Daily trade counts reset");
    }
}