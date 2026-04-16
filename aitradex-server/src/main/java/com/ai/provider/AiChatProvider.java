package com.ai.provider;

import com.ai.model.dto.ChatRequest;
import com.ai.model.dto.ChatResponse;
import com.ai.model.dto.ModelInfo;
import com.ai.model.entity.AiModelConfig;

import java.util.List;
import java.util.function.Consumer;

public interface AiChatProvider {
    
    String getProviderId();
    
    String getProviderName();
    
    boolean isEnabled();
    
    List<ModelInfo> getSupportedModels();
    
    ChatResponse chat(ChatRequest request, AiModelConfig config);
    
    void chatStream(ChatRequest request, AiModelConfig config, Consumer<String> onChunk, Consumer<String> onComplete, Consumer<Throwable> onError);
    
    boolean testConnection(AiModelConfig config);
    
    String getDefaultBaseUrl();
    
    boolean requiresBaseUrl();
}
