package com.service;

import com.config.AppProperties;
import com.domain.entity.BrokerAccountEntity;
import com.domain.entity.OrderEntity;
import com.domain.response.OrderExecutionResult;
import com.repository.BrokerAccountRepository;
import com.repository.MarketDataRepository;
import com.repository.SystemSettingRepository;
import com.repository.TradeRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BrokerService {
    private static final Logger logger = LoggerFactory.getLogger(BrokerService.class);
    
    private final SystemSettingRepository systemSettingRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final TradeRepository tradeRepository;
    private final MarketDataRepository marketDataRepository;
    private final AppProperties properties;

    public BrokerService(SystemSettingRepository systemSettingRepository,
                         BrokerAccountRepository brokerAccountRepository,
                         TradeRepository tradeRepository,
                         MarketDataRepository marketDataRepository,
                         AppProperties properties) {
        this.systemSettingRepository = systemSettingRepository;
        this.brokerAccountRepository = brokerAccountRepository;
        this.tradeRepository = tradeRepository;
        this.marketDataRepository = marketDataRepository;
        this.properties = properties;
    }

    public Map<String, Object> currentBrokerInfo() {
        String dynamic = systemSettingRepository.getSystemSetting("active_broker");
        if (dynamic != null && !dynamic.isBlank()) {
            logger.debug("Using broker from system setting: {}", dynamic);
            return Map.of("broker", dynamic.trim().toLowerCase(Locale.ROOT), "source", "system_setting");
        }
        BrokerAccountEntity active = brokerAccountRepository.getActiveBrokerAccount();
        if (active != null && active.broker() != null) {
            logger.debug("Using broker from active account: {}", active.broker());
            return Map.of("broker", active.broker().toLowerCase(Locale.ROOT), "source", "active_account");
        }
        logger.debug("Using broker from environment: {}", properties.getBrokerMode());
        return Map.of("broker", properties.getBrokerMode().toLowerCase(Locale.ROOT), "source", "env");
    }

    public OrderExecutionResult executeOrderViaBroker(long orderId) {
        logger.info("Executing order via broker, orderId={}", orderId);
        
        String broker = String.valueOf(currentBrokerInfo().get("broker"));
        logger.info("Current broker: {}", broker);
        
        if (!"paper".equals(broker) && brokerAccountRepository.getActiveBrokerAccount() == null) {
            logger.error("No active broker account found for broker: {}", broker);
            return new OrderExecutionResult(false, "active_broker_account_required_for_" + broker, orderId, null);
        }
        if ("paper".equals(broker)) {
            logger.info("Executing order in paper trading mode");
            return paperExecuteOrder(orderId, "paper", new BigDecimal("2"));
        }
        if ("gtja".equals(broker)) {
            logger.info("Executing order via GTJA");
            return paperExecuteOrder(orderId, "gtja-sim", new BigDecimal("1"));
        }
        if ("real".equals(broker)) {
            logger.info("Executing order in real trading mode");
            return paperExecuteOrder(orderId, "real-sim", new BigDecimal("1"));
        }
        if ("okx".equals(broker)) {
            logger.info("Executing order via OKX");
            return paperExecuteOrder(orderId, "okx-sim", new BigDecimal("3"));
        }
        if ("usstock".equals(broker)) {
            logger.info("Executing order via US Stock broker");
            return paperExecuteOrder(orderId, "usstock-sim", new BigDecimal("2"));
        }
        logger.error("Unsupported broker: {}", broker);
        return new OrderExecutionResult(false, "unsupported_broker:" + broker, orderId, null);
    }

    private OrderExecutionResult paperExecuteOrder(long orderId, String prefix, BigDecimal bps) {
        OrderEntity order = tradeRepository.getOrderById(orderId);
        if (order == null) {
            return new OrderExecutionResult(false, "order_not_found", orderId, null);
        }
        String brokerOrderId = prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        tradeRepository.markOrderSubmitted(orderId, brokerOrderId);
        BigDecimal rawPrice = order.price();
        if (rawPrice == null) {
            Map<String, Object> bar = marketDataRepository.getLatestBar(order.symbol(), "1m");
            if (bar == null) {
                bar = marketDataRepository.getLatestBar(order.symbol(), "1d");
            }
            rawPrice = bar == null ? BigDecimal.ONE : (BigDecimal) bar.get("close");
        }
        BigDecimal factor = BigDecimal.ONE.add(bps.divide(new BigDecimal("10000"), 8, java.math.RoundingMode.HALF_UP));
        BigDecimal fillPrice = "buy".equals(order.side())
                ? rawPrice.multiply(factor)
                : rawPrice.divide(factor, 8, java.math.RoundingMode.HALF_UP);
        return tradeRepository.executeOrderFill(orderId, fillPrice, brokerOrderId);
    }
}
