package com.ai.model.entity;

public class AiModelConfig {
    private String provider;
    private String model;
    private String modelId;
    private String apiKey;
    private String baseUrl;
    private Double temperature;
    private Integer maxTokens;
    private boolean enabled;

    public AiModelConfig() {
    }

    public AiModelConfig(String provider, String model, String modelId, String apiKey, String baseUrl, Double temperature, Integer maxTokens, boolean enabled) {
        this.provider = provider;
        this.model = model;
        this.modelId = modelId;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
