package com.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.config.AppProperties;
import com.domain.entity.RiskRuleEntity;
import com.domain.request.SignalRequest;
import com.repository.RiskRepository;
import com.repository.TradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskServiceTest {
    private RiskService riskService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setRiskEnabled(true);
        properties.setRiskTradeFrequencyLimitSec(60);
        properties.setRiskDailyTradeLimit(10);
        properties.setRiskMaxQty(10000);
        properties.setRiskMaxNotional(new BigDecimal("1000000"));
        properties.setRiskPriceVolatilityThreshold(new BigDecimal("1"));
        properties.setRiskMaxPositionPerSymbol(100000);
        properties.setRiskMaxStrategyNotional(new BigDecimal("10000000"));

        riskService = new RiskService(
                properties,
                new StubTradeRepository(),
                new StubRiskRepository());
    }

    @Test
    void previewRiskShouldNotConsumeTradeFrequencyQuota() {
        SignalRequest signal = new SignalRequest(
                "manual_command",
                "000001.SZ",
                "buy",
                new BigDecimal("0.05"),
                new BigDecimal("12.34"),
                100,
                OffsetDateTime.now());

        assertTrue(riskService.previewRisk(signal).passed());
        assertTrue(riskService.previewRisk(signal).passed());

        assertTrue(riskService.checkRisk(signal).passed());
        assertFalse(riskService.checkRisk(signal).passed());
    }

    private static final class StubTradeRepository extends TradeRepository {
        private StubTradeRepository() {
            super(null, new ObjectMapper());
        }

        @Override
        public int getLatestPositionQuantity(String symbol) {
            return 0;
        }

        @Override
        public BigDecimal getTodayStrategyNotional(String strategyName) {
            return BigDecimal.ZERO;
        }
    }

    private static final class StubRiskRepository extends RiskRepository {
        private StubRiskRepository() {
            super(null, new ObjectMapper());
        }

        @Override
        public List<RiskRuleEntity> getEnabledRiskRules() {
            return List.of();
        }
    }
}
