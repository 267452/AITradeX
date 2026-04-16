package com.ai.model.dto;

import java.util.List;

public class ProviderCatalog {
    private String id;
    private String name;
    private String baseUrl;
    private boolean baseUrlRequired;
    private String apiKeyPlaceholder;
    private String apiKeyUrl;
    private String description;
    private List<ModelInfo> models;
    private boolean enabled = true;

    public ProviderCatalog() {
    }

    public ProviderCatalog(String id, String name, String baseUrl, boolean baseUrlRequired,
                           String apiKeyPlaceholder, String apiKeyUrl, String description,
                           List<ModelInfo> models, boolean enabled) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.baseUrlRequired = baseUrlRequired;
        this.apiKeyPlaceholder = apiKeyPlaceholder;
        this.apiKeyUrl = apiKeyUrl;
        this.description = description;
        this.models = models;
        this.enabled = enabled;
    }

    public static Builder builder() {
        return new Builder();
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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
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

    public List<ModelInfo> getModels() {
        return models;
    }

    public void setModels(List<ModelInfo> models) {
        this.models = models;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static final class Builder {
        private String id;
        private String name;
        private String baseUrl;
        private boolean baseUrlRequired;
        private String apiKeyPlaceholder;
        private String apiKeyUrl;
        private String description;
        private List<ModelInfo> models;
        private boolean enabled = true;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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

        public Builder models(List<ModelInfo> models) {
            this.models = models;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ProviderCatalog build() {
            return new ProviderCatalog(id, name, baseUrl, baseUrlRequired, apiKeyPlaceholder, apiKeyUrl, description, models, enabled);
        }
    }
}
