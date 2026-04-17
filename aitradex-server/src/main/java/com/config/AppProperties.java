package com.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String brokerMode = "paper";
    private String quoteProvider = "auto";
    private String gtjaQuoteBaseUrl = "";
    private String gtjaQuoteEndpoint = "/quote";
    private String gtjaQuoteToken = "";
    private int gtjaQuoteTimeoutSec = 5;
    private String credentialsFernetKey = "";
    private String redisUrl = "redis://redis:6379/0";
    private int riskMaxQty = 100000;
    private BigDecimal riskMaxNotional = new BigDecimal("2000000");
    private boolean riskAllowShort = false;
    private boolean riskEnabled = true;
    private int riskTradeFrequencyLimitSec = 60;
    private int riskDailyTradeLimit = 10;
    private BigDecimal riskPriceVolatilityThreshold = new BigDecimal("0.1");
    private int riskMaxPositionPerSymbol = 50000;
    private BigDecimal riskMaxStrategyNotional = new BigDecimal("10000000");
    private boolean flinkComputeEnabled = true;
    private String flinkComputeEngine = "embedded";
    private String flinkJobName = "aitradex-flink-compute";
    private String knowledgeMilvusHost = "localhost";
    private int knowledgeMilvusPort = 19530;
    private String knowledgeMilvusCollection = "knowledge_document_chunks";
    private int knowledgeEmbeddingDim = 384;
    private int knowledgeChunkSize = 500;
    private int knowledgeChunkOverlap = 80;

    public String getBrokerMode() {
        return brokerMode;
    }

    public void setBrokerMode(String brokerMode) {
        this.brokerMode = brokerMode;
    }

    public String getQuoteProvider() {
        return quoteProvider;
    }

    public void setQuoteProvider(String quoteProvider) {
        this.quoteProvider = quoteProvider;
    }

    public String getGtjaQuoteBaseUrl() {
        return gtjaQuoteBaseUrl;
    }

    public void setGtjaQuoteBaseUrl(String gtjaQuoteBaseUrl) {
        this.gtjaQuoteBaseUrl = gtjaQuoteBaseUrl;
    }

    public String getGtjaQuoteEndpoint() {
        return gtjaQuoteEndpoint;
    }

    public void setGtjaQuoteEndpoint(String gtjaQuoteEndpoint) {
        this.gtjaQuoteEndpoint = gtjaQuoteEndpoint;
    }

    public String getGtjaQuoteToken() {
        return gtjaQuoteToken;
    }

    public void setGtjaQuoteToken(String gtjaQuoteToken) {
        this.gtjaQuoteToken = gtjaQuoteToken;
    }

    public int getGtjaQuoteTimeoutSec() {
        return gtjaQuoteTimeoutSec;
    }

    public void setGtjaQuoteTimeoutSec(int gtjaQuoteTimeoutSec) {
        this.gtjaQuoteTimeoutSec = gtjaQuoteTimeoutSec;
    }

    public String getCredentialsFernetKey() {
        return credentialsFernetKey;
    }

    public void setCredentialsFernetKey(String credentialsFernetKey) {
        this.credentialsFernetKey = credentialsFernetKey;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public void setRedisUrl(String redisUrl) {
        this.redisUrl = redisUrl;
    }

    public int getRiskMaxQty() {
        return riskMaxQty;
    }

    public void setRiskMaxQty(int riskMaxQty) {
        this.riskMaxQty = riskMaxQty;
    }

    public BigDecimal getRiskMaxNotional() {
        return riskMaxNotional;
    }

    public void setRiskMaxNotional(BigDecimal riskMaxNotional) {
        this.riskMaxNotional = riskMaxNotional;
    }

    public boolean isRiskAllowShort() {
        return riskAllowShort;
    }

    public void setRiskAllowShort(boolean riskAllowShort) {
        this.riskAllowShort = riskAllowShort;
    }

    public boolean isRiskEnabled() {
        return riskEnabled;
    }

    public void setRiskEnabled(boolean riskEnabled) {
        this.riskEnabled = riskEnabled;
    }

    public int getRiskTradeFrequencyLimitSec() {
        return riskTradeFrequencyLimitSec;
    }

    public void setRiskTradeFrequencyLimitSec(int riskTradeFrequencyLimitSec) {
        this.riskTradeFrequencyLimitSec = riskTradeFrequencyLimitSec;
    }

    public int getRiskDailyTradeLimit() {
        return riskDailyTradeLimit;
    }

    public void setRiskDailyTradeLimit(int riskDailyTradeLimit) {
        this.riskDailyTradeLimit = riskDailyTradeLimit;
    }

    public BigDecimal getRiskPriceVolatilityThreshold() {
        return riskPriceVolatilityThreshold;
    }

    public void setRiskPriceVolatilityThreshold(BigDecimal riskPriceVolatilityThreshold) {
        this.riskPriceVolatilityThreshold = riskPriceVolatilityThreshold;
    }

    public int getRiskMaxPositionPerSymbol() {
        return riskMaxPositionPerSymbol;
    }

    public void setRiskMaxPositionPerSymbol(int riskMaxPositionPerSymbol) {
        this.riskMaxPositionPerSymbol = riskMaxPositionPerSymbol;
    }

    public BigDecimal getRiskMaxStrategyNotional() {
        return riskMaxStrategyNotional;
    }

    public void setRiskMaxStrategyNotional(BigDecimal riskMaxStrategyNotional) {
        this.riskMaxStrategyNotional = riskMaxStrategyNotional;
    }

    public boolean isFlinkComputeEnabled() {
        return flinkComputeEnabled;
    }

    public void setFlinkComputeEnabled(boolean flinkComputeEnabled) {
        this.flinkComputeEnabled = flinkComputeEnabled;
    }

    public String getFlinkComputeEngine() {
        return flinkComputeEngine;
    }

    public void setFlinkComputeEngine(String flinkComputeEngine) {
        this.flinkComputeEngine = flinkComputeEngine;
    }

    public String getFlinkJobName() {
        return flinkJobName;
    }

    public void setFlinkJobName(String flinkJobName) {
        this.flinkJobName = flinkJobName;
    }

    public String getKnowledgeMilvusHost() {
        return knowledgeMilvusHost;
    }

    public void setKnowledgeMilvusHost(String knowledgeMilvusHost) {
        this.knowledgeMilvusHost = knowledgeMilvusHost;
    }

    public int getKnowledgeMilvusPort() {
        return knowledgeMilvusPort;
    }

    public void setKnowledgeMilvusPort(int knowledgeMilvusPort) {
        this.knowledgeMilvusPort = knowledgeMilvusPort;
    }

    public String getKnowledgeMilvusCollection() {
        return knowledgeMilvusCollection;
    }

    public void setKnowledgeMilvusCollection(String knowledgeMilvusCollection) {
        this.knowledgeMilvusCollection = knowledgeMilvusCollection;
    }

    public int getKnowledgeEmbeddingDim() {
        return knowledgeEmbeddingDim;
    }

    public void setKnowledgeEmbeddingDim(int knowledgeEmbeddingDim) {
        this.knowledgeEmbeddingDim = knowledgeEmbeddingDim;
    }

    public int getKnowledgeChunkSize() {
        return knowledgeChunkSize;
    }

    public void setKnowledgeChunkSize(int knowledgeChunkSize) {
        this.knowledgeChunkSize = knowledgeChunkSize;
    }

    public int getKnowledgeChunkOverlap() {
        return knowledgeChunkOverlap;
    }

    public void setKnowledgeChunkOverlap(int knowledgeChunkOverlap) {
        this.knowledgeChunkOverlap = knowledgeChunkOverlap;
    }
}
