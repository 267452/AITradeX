package com.ai.service;

import com.ai.config.AiProperties;
import com.ai.factory.AiProviderFactory;
import com.ai.model.dto.ChatRequest;
import com.ai.model.dto.ChatResponse;
import com.ai.model.dto.ProviderCatalog;
import com.ai.model.entity.AiModelConfig;
import com.domain.entity.AiConfigEntity;
import com.domain.request.AiConfigRequest;
import com.domain.response.AiConfigResponse;
import com.domain.response.AiModelsResponse;
import com.domain.response.AiOperationResponse;
import com.domain.response.AiSavedConfigResponse;
import com.domain.response.AiSwitchModelResponse;
import com.domain.response.AiTestResponse;
import com.ai.provider.AiChatProvider;
import com.repository.AiConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class AiChatService {
    
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final String MASKED_API_KEY = "••••••••";
    
    private final AiProviderFactory providerFactory;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiConfigRepository aiConfigRepository;
    
    private final Map<String, AiModelConfig> currentConfigs = new ConcurrentHashMap<>();
    private String currentProviderId;
    private String currentModel;
    
    public AiChatService(AiProviderFactory providerFactory, AiProperties aiProperties, 
                         ObjectMapper objectMapper, AiConfigRepository aiConfigRepository) {
        this.providerFactory = providerFactory;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.aiConfigRepository = aiConfigRepository;
        initializeDefaultConfig();
    }
    
    private void initializeDefaultConfig() {
        AiConfigEntity dbConfig = aiConfigRepository.getActiveConfig();
        if (dbConfig != null) {
            this.currentProviderId = dbConfig.provider();
            this.currentModel = dbConfig.model();
            AiModelConfig config = new AiModelConfig();
            config.setProvider(dbConfig.provider());
            config.setModel(dbConfig.model());
            config.setModelId(dbConfig.modelId());
            config.setApiKey(dbConfig.apiKeyEncrypted());
            config.setBaseUrl(dbConfig.baseUrl());
            config.setTemperature(dbConfig.temperature() != null ? dbConfig.temperature() : aiProperties.getDefaultTemperature());
            config.setMaxTokens(dbConfig.maxTokens() != null ? dbConfig.maxTokens() : aiProperties.getDefaultMaxTokens());
            config.setEnabled(dbConfig.enabled() != null ? dbConfig.enabled() : true);
            currentConfigs.put(dbConfig.provider(), config);
            log.info("Loaded AI config from database: provider={}, model={}, modelId={}, baseUrl={}", currentProviderId, currentModel, config.getModelId(), config.getBaseUrl());
            return;
        }
        
        this.currentProviderId = aiProperties.getDefaultProvider();
        this.currentModel = aiProperties.getDefaultModel();
        
        AiProperties.ProviderConfig providerConfig = aiProperties.getProviders().get(currentProviderId);
        if (providerConfig != null && providerConfig.getApiKey() != null) {
            AiModelConfig config = new AiModelConfig();
            config.setProvider(currentProviderId);
            config.setModel(providerConfig.getModel() != null ? providerConfig.getModel() : currentModel);
            config.setApiKey(providerConfig.getApiKey());
            config.setBaseUrl(providerConfig.getBaseUrl());
            config.setTemperature(providerConfig.getTemperature() != null ? providerConfig.getTemperature() : aiProperties.getDefaultTemperature());
            config.setMaxTokens(providerConfig.getMaxTokens() != null ? providerConfig.getMaxTokens() : aiProperties.getDefaultMaxTokens());
            config.setEnabled(true);
            currentConfigs.put(currentProviderId, config);
            log.info("Loaded default AI config: provider={}, model={}", currentProviderId, config.getModel());
        }
    }
    
    public AiConfigResponse getConfig() {
        AiModelConfig config = currentConfigs.get(currentProviderId);
        if (config != null) {
            return new AiConfigResponse(
                    currentProviderId,
                    currentModel,
                    config.getModelId(),
                    config.getBaseUrl(),
                    config.getApiKey() != null && !config.getApiKey().isBlank());
        }
        return new AiConfigResponse(currentProviderId, currentModel, "", "", false);
    }

    public List<AiSavedConfigResponse> listSavedConfigs() {
        List<AiSavedConfigResponse> result = new java.util.ArrayList<>();
        for (AiConfigEntity entity : aiConfigRepository.listAllConfigs()) {
            result.add(new AiSavedConfigResponse(
                    entity.id(),
                    entity.provider(),
                    entity.model(),
                    entity.modelId(),
                    entity.baseUrl(),
                    entity.active(),
                    entity.updatedAt()));
        }
        return result;
    }
    
    public AiOperationResponse saveConfig(AiConfigRequest config) {
        String provider = normalize(config.provider());
        String model = normalize(config.model());
        String modelId = normalize(config.modelId());
        String apiKey = resolveApiKeyForProvider(config.apiKey(), provider);
        String baseUrl = normalize(config.baseUrl());

        log.info("saveConfig: provider={}, model={}, modelId={}, baseUrl={}", provider, model, modelId, baseUrl);

        AiChatProvider chatProvider = providerFactory.getProvider(provider);

        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("Provider is required");
        }
        if (!providerFactory.isProviderSupported(provider)) {
            throw new IllegalStateException("Unsupported provider: " + provider);
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("Model is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API key is required");
        }
        if (chatProvider.requiresBaseUrl() && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalStateException("Base URL is required for provider: " + provider);
        }

        String effectiveBaseUrl = baseUrl.isBlank() ? chatProvider.getDefaultBaseUrl() : baseUrl;
        log.info("effectiveBaseUrl: {}, original baseUrl: {}", effectiveBaseUrl, baseUrl);

        AiModelConfig modelConfig = new AiModelConfig();
        modelConfig.setProvider(provider);
        modelConfig.setModel(model);
        modelConfig.setModelId(modelId);
        modelConfig.setApiKey(apiKey);
        modelConfig.setBaseUrl(effectiveBaseUrl);
        log.info("modelConfig baseUrl after set: {}", modelConfig.getBaseUrl());
        modelConfig.setTemperature(aiProperties.getDefaultTemperature());
        modelConfig.setMaxTokens(aiProperties.getDefaultMaxTokens());
        modelConfig.setEnabled(true);

        currentConfigs.put(provider, modelConfig);
        currentProviderId = provider;
        currentModel = model;

        AiConfigEntity entity = new AiConfigEntity(
            null, provider, model, modelId, apiKey, effectiveBaseUrl,
            aiProperties.getDefaultTemperature(), aiProperties.getDefaultMaxTokens(),
            true, true, null, null
        );
        aiConfigRepository.saveOrUpdate(entity);
        aiConfigRepository.setActive(provider);

        log.info("AI config saved: provider={}, model={}", provider, model);
        return new AiOperationResponse(true, "配置已保存");
    }
    
    public AiOperationResponse clearConfig() {
        String provider = currentProviderId;
        if (provider != null) {
            currentConfigs.remove(provider);
            aiConfigRepository.delete(provider);
        }
        currentProviderId = aiProperties.getDefaultProvider();
        currentModel = aiProperties.getDefaultModel();
        log.info("AI config cleared");
        return new AiOperationResponse(true, "配置已清空");
    }
    
    public ChatResponse chat(String userMessage, String provider, String model, String systemPrompt) {
        AiChatProvider chatProvider = resolveProvider(provider);
        AiModelConfig config = resolveConfig(chatProvider, model);

        ChatRequest request = new ChatRequest();
        request.setMessage(userMessage);
        request.setSystemPrompt(systemPrompt != null ? systemPrompt : "");
        request.setProvider(provider);
        request.setModel(model);

        return chatProvider.chat(request, config);
    }

    public String simpleChat(String userMessage) {
        log.info("simpleChat called: currentProviderId={}, currentModel={}", currentProviderId, currentModel);
        ChatResponse response = chat(userMessage, null, null, null);
        if (!response.isSuccess()) {
            return null;
        }
        return response.getContent();
    }
    
    public String generateText(String userMessage, String provider, String model, String systemPrompt) {
        ChatResponse response = chat(userMessage, provider, model, systemPrompt);
        if (!response.isSuccess()) {
            return null;
        }
        return response.getContent();
    }
    
    public void chatStream(String userMessage, String provider, String model, String systemPrompt,
                          Consumer<String> onChunk, Consumer<String> onComplete, Consumer<Throwable> onError) {
        AiChatProvider chatProvider = resolveProvider(provider);
        AiModelConfig config = resolveConfig(chatProvider, model);
        
        ChatRequest request = new ChatRequest();
        request.setMessage(userMessage);
        request.setSystemPrompt(systemPrompt);
        request.setProvider(provider);
        request.setModel(model);
        request.setStream(true);
        
        chatProvider.chatStream(request, config, onChunk, onComplete, onError);
    }
    
    public AiTestResponse testConnection(AiConfigRequest config) {
        try {
            String provider = normalize(config.provider());
            String model = normalize(config.model());
            String apiKey = resolveApiKeyForProvider(config.apiKey(), provider);
            String baseUrl = normalize(config.baseUrl());
            
            if (!providerFactory.isProviderSupported(provider)) {
                return new AiTestResponse(false, null, "不支持的服务商: " + provider);
            }
            
            AiChatProvider chatProvider = providerFactory.getProvider(provider);

            String effectiveBaseUrl = baseUrl.isBlank() ? chatProvider.getDefaultBaseUrl() : baseUrl;

            AiModelConfig modelConfig = new AiModelConfig();
            modelConfig.setProvider(provider);
            modelConfig.setModel(model);
            modelConfig.setApiKey(apiKey);
            modelConfig.setBaseUrl(effectiveBaseUrl);
            modelConfig.setTemperature(aiProperties.getDefaultTemperature());
            modelConfig.setMaxTokens(aiProperties.getDefaultMaxTokens());
            
            boolean success = chatProvider.testConnection(modelConfig);
            if (success) {
                return new AiTestResponse(true, "连接成功", null);
            } else {
                return new AiTestResponse(false, null, "连接失败，请检查配置");
            }
        } catch (Exception e) {
            return new AiTestResponse(false, null, e.getMessage());
        }
    }
    
    public AiModelsResponse listModels() {
        List<ProviderCatalog> providers = providerFactory.getAllProviderCatalogs();
        return new AiModelsResponse(currentProviderId, currentModel, providers);
    }
    
    public AiSwitchModelResponse switchModel(String provider, String model, String modelId) {
        if (!providerFactory.isProviderSupported(provider)) {
            return new AiSwitchModelResponse(false, "模型不存在：" + provider, provider, model, modelId);
        }

        AiConfigEntity dbConfig = aiConfigRepository.getByProvider(provider);
        if (dbConfig != null) {
            AiModelConfig config = new AiModelConfig();
            config.setProvider(dbConfig.provider());
            config.setModel(dbConfig.model());
            config.setModelId(modelId != null ? modelId : dbConfig.modelId());
            config.setApiKey(dbConfig.apiKeyEncrypted());
            config.setBaseUrl(dbConfig.baseUrl());
            config.setTemperature(dbConfig.temperature() != null ? dbConfig.temperature() : aiProperties.getDefaultTemperature());
            config.setMaxTokens(dbConfig.maxTokens() != null ? dbConfig.maxTokens() : aiProperties.getDefaultMaxTokens());
            config.setEnabled(true);
            currentConfigs.put(provider, config);
        }

        this.currentProviderId = provider;
        this.currentModel = model;

        aiConfigRepository.setActive(provider);

        return new AiSwitchModelResponse(true, "已切换到模型：" + model + " (" + modelId + ")", provider, model, modelId);
    }
    
    private AiChatProvider resolveProvider(String provider) {
        if (provider != null && !provider.isBlank()) {
            return providerFactory.getProvider(provider);
        }
        return providerFactory.getProvider(currentProviderId);
    }
    
    private AiModelConfig resolveConfig(AiChatProvider provider, String model) {
        String providerId = provider.getProviderId();
        String modelIdFromParam = model;

        log.info("resolveConfig START: providerId={}, modelIdFromParam={}, currentModel={}, currentConfigs keys={}, all configs={}",
                providerId, modelIdFromParam, currentModel, currentConfigs.keySet(), currentConfigs);

        AiModelConfig cachedConfig = currentConfigs.get(providerId);
        log.info("resolveConfig: cachedConfig lookup for '{}' = {}", providerId, cachedConfig);
        if (cachedConfig != null && cachedConfig.isEnabled()) {
            String effectiveModelId = modelIdFromParam != null && !modelIdFromParam.isBlank()
                    ? modelIdFromParam
                    : (cachedConfig.getModelId() != null && !cachedConfig.getModelId().isBlank()
                        ? cachedConfig.getModelId()
                        : cachedConfig.getModel());
            cachedConfig.setModel(effectiveModelId);
            log.info("Using cached config: baseUrl={}, model={} (modelId={}), apiKey present={}",
                    cachedConfig.getBaseUrl(), cachedConfig.getModel(), cachedConfig.getModelId(), cachedConfig.getApiKey() != null);
            return cachedConfig;
        }

        log.info("resolveConfig: cachedConfig is null or not enabled, checking aiProperties providers");
        AiProperties.ProviderConfig providerConfig = aiProperties.getProviders().get(providerId);
        log.info("resolveConfig: providerConfig from aiProperties = {}", providerConfig);
        if (providerConfig != null && providerConfig.isEnabled()) {
            AiModelConfig config = new AiModelConfig();
            config.setProvider(providerId);
            config.setModel(providerConfig.getModel() != null ? providerConfig.getModel() : modelIdFromParam);
            config.setApiKey(providerConfig.getApiKey());
            config.setBaseUrl(providerConfig.getBaseUrl());
            config.setTemperature(providerConfig.getTemperature() != null ? providerConfig.getTemperature() : aiProperties.getDefaultTemperature());
            config.setMaxTokens(providerConfig.getMaxTokens() != null ? providerConfig.getMaxTokens() : aiProperties.getDefaultMaxTokens());
            config.setEnabled(true);
            log.info("resolveConfig: using providerConfig, baseUrl={}, model={}", config.getBaseUrl(), config.getModel());
            return config;
        }

        log.error("resolveConfig: NO CONFIG FOUND! throwing exception");
        throw new IllegalStateException("当前没有可用的 AI 模型配置，请先在配置页面设置服务商、模型和 API Key");
    }
    
    private String resolveApiKeyForProvider(String requestedApiKey, String provider) {
        String normalized = normalize(requestedApiKey);
        log.info("resolveApiKeyForProvider: requestedApiKey={}, isMasked={}, provider={}", requestedApiKey, MASKED_API_KEY.equals(normalized), provider);
        if (MASKED_API_KEY.equals(normalized)) {
            AiModelConfig config = currentConfigs.get(provider);
            log.info("resolveApiKeyForProvider: config from currentConfigs for '{}' = {}", provider, config);
            if (config != null) {
                log.info("resolveApiKeyForProvider: returning stored apiKey, length={}", config.getApiKey() != null ? config.getApiKey().length() : 0);
                return config.getApiKey();
            }
            log.warn("resolveApiKeyForProvider: config is null for provider {}", provider);
            return null;
        }
        return normalized;
    }
    
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
    
    public Map<String, Object> parseTradingInstruction(String aiResponse, String originalInput) {
        try {
            String cleaned = aiResponse.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("```\\s*$", "")
                    .trim();
            
            Map<String, Object> data = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
            
            String action = extractJsonField(cleaned, "action");
            String symbol = extractJsonField(cleaned, "symbol");
            String quantityStr = extractJsonField(cleaned, "quantity");
            String reason = extractJsonField(cleaned, "reason");
            
            if ("unknown".equals(action)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "无法转换为交易指令：" + (reason != null ? reason : "未知原因"));
                result.put("command", null);
                result.put("ai_reason", reason);
                return result;
            }
            
            int quantity = 100;
            if (quantityStr != null && !quantityStr.isBlank()) {
                try {
                    quantity = Integer.parseInt(quantityStr.replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    quantity = 100;
                }
            }
            
            String normalizedSymbol = normalizeSymbol(symbol);
            String command = formatCommand(action, normalizedSymbol, quantity);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已转换为指令：" + command);
            result.put("command", command);
            result.put("parsed", Map.of(
                    "action", action != null ? action : "unknown",
                    "symbol", normalizedSymbol,
                    "quantity", quantity,
                    "reason", reason != null ? reason : ""
            ));
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "解析AI响应失败：" + e.getMessage());
            result.put("command", null);
            result.put("ai_response", aiResponse);
            return result;
        }
    }
    
    private String extractJsonField(String json, String field) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"?([^\",}]+)\"?");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    private String normalizeSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return "";
        }
        String s = rawSymbol.trim().toUpperCase();
        
        if (s.contains("-USDT") || s.contains("-USD")) {
            return s;
        }
        
        if (s.matches("\\d{6}")) {
            if (s.startsWith("6")) {
                return s + ".SH";
            } else if (s.startsWith("0") || s.startsWith("3")) {
                return s + ".SZ";
            } else if (s.startsWith("4") || s.startsWith("8") || s.startsWith("9")) {
                return s + ".BJ";
            }
        }
        
        return s;
    }
    
    private String formatCommand(String action, String symbol, int quantity) {
        return switch (action) {
            case "buy" -> "买入 " + symbol + " " + quantity;
            case "sell" -> "卖出 " + symbol + " " + quantity;
            case "strategy" -> "运行策略 " + symbol + " " + quantity;
            default -> "";
        };
    }
    
    public String getCurrentProviderId() {
        return currentProviderId;
    }
    
    public String getCurrentModel() {
        return currentModel;
    }
}
