package com.ai.provider;

import com.ai.model.dto.ModelInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenAiProvider extends AbstractAiChatProvider {
    
    private static final String PROVIDER_ID = "openai";
    private static final String PROVIDER_NAME = "OpenAI";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    
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
        return false;
    }
    
    @Override
    public List<ModelInfo> getSupportedModels() {
        return List.of(
                ModelInfo.builder()
                        .id("gpt-5.2")
                        .name("GPT-5.2")
                        .modelId("gpt-5.2")
                        .build(),
                ModelInfo.builder()
                        .id("gpt-5")
                        .name("GPT-5")
                        .modelId("gpt-5")
                        .build(),
                ModelInfo.builder()
                        .id("gpt-5-mini")
                        .name("GPT-5 mini")
                        .modelId("gpt-5-mini")
                        .build(),
                ModelInfo.builder()
                        .id("gpt-4.1")
                        .name("GPT-4.1")
                        .modelId("gpt-4.1")
                        .build(),
                ModelInfo.builder()
                        .id("gpt-4.1-mini")
                        .name("GPT-4.1 mini")
                        .modelId("gpt-4.1-mini")
                        .build(),
                ModelInfo.builder()
                        .id("gpt-4o")
                        .name("GPT-4o")
                        .modelId("gpt-4o")
                        .build(),
                ModelInfo.builder()
                        .id("gpt-4o-mini")
                        .name("GPT-4o mini")
                        .modelId("gpt-4o-mini")
                        .build(),
                ModelInfo.builder()
                        .id("gpt-4-turbo")
                        .name("GPT-4 Turbo")
                        .modelId("gpt-4-turbo")
                        .build(),
                ModelInfo.builder()
                        .id("gpt-3.5-turbo")
                        .name("GPT-3.5 Turbo")
                        .modelId("gpt-3.5-turbo")
                        .build()
        );
    }
}
