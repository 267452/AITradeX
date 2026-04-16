package com.ai.provider;

import com.ai.model.dto.ChatRequest;
import com.ai.model.dto.ChatResponse;
import com.ai.model.entity.AiModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public abstract class AbstractAiChatProvider implements AiChatProvider {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected static final double DEFAULT_TEMPERATURE = 0.3;
    
    @Override
    public boolean testConnection(AiModelConfig config) {
        try {
            ChatLanguageModel model = buildChatModel(config);
            String testResponse = model.generate("请简短回复'连接成功'");
            return testResponse != null && !testResponse.isBlank();
        } catch (Exception e) {
            log.error("Connection test failed for provider {}: {}", getProviderId(), e.getMessage(), e);
            throw new RuntimeException("连接失败: " + e.getMessage(), e);
        }
    }
    
    protected ChatLanguageModel buildChatModel(AiModelConfig config) {
        var builder = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .temperature(config.getTemperature() != null ? config.getTemperature() : DEFAULT_TEMPERATURE);
        
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            builder.baseUrl(config.getBaseUrl());
        }
        
        if (config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }
        
        return builder.build();
    }
    
    @Override
    public ChatResponse chat(ChatRequest request, AiModelConfig config) {
        try {
            ChatLanguageModel model = buildChatModel(config);
            String fullPrompt = buildPrompt(request);
            String response = model.generate(fullPrompt);
            return ChatResponse.success(response);
        } catch (Exception e) {
            log.error("Chat failed for provider {}: {}", getProviderId(), e.getMessage());
            return ChatResponse.error("AI模型调用失败: " + e.getMessage());
        }
    }
    
    @Override
    public void chatStream(ChatRequest request, AiModelConfig config, 
                          Consumer<String> onChunk, 
                          Consumer<String> onComplete, 
                          Consumer<Throwable> onError) {
        onError.accept(new UnsupportedOperationException("Streaming not supported in this version"));
    }
    
    protected String buildPrompt(ChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            prompt.append(request.getSystemPrompt()).append("\n\n");
        }
        prompt.append("用户输入：").append(request.getMessage());
        return prompt.toString();
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public boolean requiresBaseUrl() {
        return false;
    }
}
