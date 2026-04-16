package com.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ModelInfo {
    private String id;
    private String name;
    private String modelId;
    private String apiHost;
    private boolean baseUrlRequired;
    private String apiKeyPlaceholder;
    private String apiKeyUrl;
    private String description;

    public ModelInfo() {
    }

    public ModelInfo(String id, String name, String modelId, String apiHost, boolean baseUrlRequired,
                     String apiKeyPlaceholder, String apiKeyUrl, String description) {
        this.id = id;
        this.name = name;
        this.modelId = modelId;
        this.apiHost = apiHost;
        this.baseUrlRequired = baseUrlRequired;
        this.apiKeyPlaceholder = apiKeyPlaceholder;
        this.apiKeyUrl = apiKeyUrl;
        this.description = description;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("value")
    public String getValue() {
        return id != null ? id : name;
    }

    @JsonProperty("label")
    public String getLabel() {
        return name != null ? name : id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public boolean isBaseUrlRequired() {
        return baseUrlRequired;
    }

    public void setBaseUrlRequired(boolean baseUrlRequired) {
        this.baseUrlRequired = baseUrlRequired;
    }

    public String getApiKeyPlaceholder() {
        return apiKeyPlaceholder;
    }

    public void setApiKeyPlaceholder(String apiKeyPlaceholder) {
        this.apiKeyPlaceholder = apiKeyPlaceholder;
    }

    public String getApiKeyUrl() {
        return apiKeyUrl;
    }

    public void setApiKeyUrl(String apiKeyUrl) {
        this.apiKeyUrl = apiKeyUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static final class Builder {
        private String id;
        private String name;
        private String modelId;
        private String apiHost;
        private boolean baseUrlRequired;
        private String apiKeyPlaceholder;
        private String apiKeyUrl;
        private String description;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder apiHost(String apiHost) {
            this.apiHost = apiHost;
            return this;
        }

        public Builder baseUrlRequired(boolean baseUrlRequired) {
            this.baseUrlRequired = baseUrlRequired;
            return this;
        }

        public Builder apiKeyPlaceholder(String apiKeyPlaceholder) {
            this.apiKeyPlaceholder = apiKeyPlaceholder;
            return this;
        }

        public Builder apiKeyUrl(String apiKeyUrl) {
            this.apiKeyUrl = apiKeyUrl;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public ModelInfo build() {
            return new ModelInfo(id, name, modelId, apiHost, baseUrlRequired, apiKeyPlaceholder, apiKeyUrl, description);
        }
    }
}
