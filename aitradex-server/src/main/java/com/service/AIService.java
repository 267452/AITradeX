package com.service;

import com.ai.service.AiChatService;
import com.domain.request.AiConfigRequest;
import com.domain.response.AiConfigResponse;
import com.domain.response.AiModelsResponse;
import com.domain.response.AiOperationResponse;
import com.domain.response.AiSavedConfigResponse;
import com.domain.response.AiSwitchModelResponse;
import com.domain.response.AiTestResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AIService {
    
    private static final String SYSTEM_PROMPT = """
            你是一个专业的量化交易助手，名为AIBuy助手。你的任务是理解用户的自然语言交易指令，并将其转换为标准化的交易指令格式。

            支持的交易指令格式：
            1. 买入：买入 [股票代码] [数量] 或 买入 [数字货币] [数量]
            2. 卖出：卖出 [股票代码] [数量] 或 卖出 [数字货币] [数量]
            3. 运行策略：运行策略 [标的代码] [数量]

            股票代码规则：
            - A股6位数代码，以6开头自动添加.SH后缀
            - A股6位数代码，以0或3开头自动添加.SZ后缀
            - 数字货币使用如 BTC-USDT、ETH-USDT 格式

            请严格按照以下JSON格式输出，不要包含任何其他内容：
            {
              "action": "buy|sell|strategy|unknown",
              "symbol": "标的代码",
              "quantity": 数量,
              "reason": "转换理由"
            }

            如果用户输入不明确或无法转换为有效指令，action设为"unknown"，reason说明原因。
            如果用户只是询问或闲聊，也返回action为"unknown"。
            数量必须是整数，最小为1。

            现在请处理用户的输入：
            """;
    
    private final AiChatService aiChatService;
    
    public AIService(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }
    
    public AiConfigResponse getConfig() {
        return aiChatService.getConfig();
    }
    
    public AiOperationResponse saveConfig(AiConfigRequest config) {
        return aiChatService.saveConfig(config);
    }
    
    public AiOperationResponse clearConfig() {
        return aiChatService.clearConfig();
    }
    
    public Map<String, Object> chat(String userMessage, String provider, String model) {
        String response = aiChatService.generateText(userMessage, provider, model, SYSTEM_PROMPT);
        if (response == null) {
            return Map.of(
                    "success", false,
                    "message", "当前没有可用的AI模型，请先在配置页面设置服务商、模型和API Key",
                    "command", null
            );
        }
        return aiChatService.parseTradingInstruction(response, userMessage);
    }

    public Map<String, Object> simpleChat(String userMessage) {
        String response = aiChatService.simpleChat(userMessage);
        if (response == null) {
            return Map.of("success", false, "message", "当前没有可用的AI模型，请先在配置页面设置服务商、模型和API Key");
        }
        return Map.of("success", true, "content", response);
    }

    public String generateText(String userMessage, String provider, String model, String systemPrompt) {
        return aiChatService.generateText(userMessage, provider, model, systemPrompt);
    }
    
    public Map<String, Object> parseTradingInstruction(String aiResponse, String originalInput) {
        return aiChatService.parseTradingInstruction(aiResponse, originalInput);
    }
    
    public AiTestResponse testConnection(AiConfigRequest config) {
        return aiChatService.testConnection(config);
    }
    
    public AiModelsResponse listModels() {
        return aiChatService.listModels();
    }

    public List<AiSavedConfigResponse> listSavedConfigs() {
        return aiChatService.listSavedConfigs();
    }

    public AiSwitchModelResponse switchModel(String provider, String modelName) {
        String effectiveProvider = provider != null && !provider.isBlank() ? provider : aiChatService.getCurrentProviderId();
        return aiChatService.switchModel(effectiveProvider, modelName);
    }
}
