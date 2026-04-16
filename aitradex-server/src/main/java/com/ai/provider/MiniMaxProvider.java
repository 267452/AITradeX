package com.ai.provider;

import com.ai.model.dto.ModelInfo;
import com.ai.model.entity.AiModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MiniMaxProvider extends AbstractAiChatProvider {
    
    private static final String PROVIDER_ID = "minimax";
    private static final String PROVIDER_NAME = "MiniMax";
    private static final String DEFAULT_BASE_URL = "https://api.minimaxi.com/v1";
    
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
                        .id("MiniMax-M2.7")
                        .name("MiniMax-M2.7")
                        .modelId("MiniMax-M2.7")
                        .description("旗舰文本模型，面向复杂推理与多轮对话")
                        .build(),
                ModelInfo.builder()
                        .id("MiniMax-M2.7-highspeed")
                        .name("MiniMax-M2.7-highspeed")
                        .modelId("MiniMax-M2.7-highspeed")
                        .description("M2.7 高速版，效果一致、响应更快")
                        .build(),
                ModelInfo.builder()
                        .id("MiniMax-M2.5")
                        .name("MiniMax-M2.5")
                        .modelId("MiniMax-M2.5")
                        .description("高性价比主力模型，适配多数文本任务")
                        .build(),
                ModelInfo.builder()
                        .id("MiniMax-M2.5-highspeed")
                        .name("MiniMax-M2.5-highspeed")
                        .modelId("MiniMax-M2.5-highspeed")
                        .description("M2.5 高速版，时延更低")
                        .build(),
                ModelInfo.builder()
                        .id("MiniMax-M2.1")
                        .name("MiniMax-M2.1")
                        .modelId("MiniMax-M2.1")
                        .description("强大编程能力与通用文本理解能力")
                        .build(),
                ModelInfo.builder()
                        .id("MiniMax-M2.1-highspeed")
                        .name("MiniMax-M2.1-highspeed")
                        .modelId("MiniMax-M2.1-highspeed")
                        .description("M2.1 高速版，推理速度更高")
                        .build(),
                ModelInfo.builder()
                        .id("MiniMax-M2")
                        .name("MiniMax-M2")
                        .modelId("MiniMax-M2")
                        .description("面向编码与 Agent 工作流场景")
                        .build()
        );
    }
    
    @Override
    protected ChatLanguageModel buildChatModel(AiModelConfig config) {
        String baseUrl = config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                ? DEFAULT_BASE_URL
                : config.getBaseUrl();

        String apiKey = config.getApiKey();
        System.out.println("MiniMaxProvider.buildChatModel: apiKey length=" + (apiKey != null ? apiKey.length() : "null") + ", baseUrl=" + baseUrl + ", model=" + config.getModel());

        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModel())
                .temperature(config.getTemperature() != null ? config.getTemperature() : DEFAULT_TEMPERATURE)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(120));

        if (config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }

        return builder.build();
    }
}
