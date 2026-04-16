package com.ai.provider;

import com.ai.model.dto.ModelInfo;
import com.ai.model.entity.AiModelConfig;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomProvider extends AbstractAiChatProvider {
    
    private static final String PROVIDER_ID = "custom";
    private static final String PROVIDER_NAME = "自定义 OpenAI 兼容接口";
    private static final String DEFAULT_BASE_URL = "https://your-api-endpoint.com/v1";
    
    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }
    
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
    
    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }
    
    @Override
    public boolean requiresBaseUrl() {
        return true;
    }
    
    @Override
    public List<ModelInfo> getSupportedModels() {
        return List.of(
                ModelInfo.builder()
                        .id("custom-model")
                        .name("自定义模型（手动填写兼容模型名）")
                        .modelId("custom-model")
                        .description("适用于其他 OpenAI 兼容的 API 服务")
                        .build()
        );
    }
    
    @Override
    public boolean testConnection(AiModelConfig config) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            log.error("Custom provider requires baseUrl");
            return false;
        }
        return super.testConnection(config);
    }
}
