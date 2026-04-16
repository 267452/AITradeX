package com.ai.factory;

import com.ai.model.dto.ProviderCatalog;
import com.ai.provider.AiChatProvider;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AiProviderFactory implements ApplicationContextAware {
    
    private static final List<String> PROVIDER_ORDER = Arrays.asList("minimax", "openai", "custom");
    
    private final Map<String, AiChatProvider> providerMap = new LinkedHashMap<>();
    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    @PostConstruct
    public void init() {
        Map<String, AiChatProvider> providers = applicationContext.getBeansOfType(AiChatProvider.class);
        for (AiChatProvider provider : providers.values()) {
            if (provider != null && provider.isEnabled()) {
                providerMap.put(provider.getProviderId(), provider);
            }
        }
    }
    
    public AiChatProvider getProvider(String providerId) {
        AiChatProvider provider = providerMap.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的 AI 提供商: " + providerId);
        }
        return provider;
    }
    
    public Optional<AiChatProvider> findProvider(String providerId) {
        return Optional.ofNullable(providerMap.get(providerId));
    }
    
    public boolean isProviderSupported(String providerId) {
        return providerMap.containsKey(providerId);
    }
    
    public List<String> getSupportedProviderIds() {
        return getSortedProviders().stream()
                .map(AiChatProvider::getProviderId)
                .collect(Collectors.toList());
    }
    
    public List<ProviderCatalog> getAllProviderCatalogs() {
        List<ProviderCatalog> catalogs = new ArrayList<>();
        for (AiChatProvider provider : getSortedProviders()) {
            catalogs.add(ProviderCatalog.builder()
                    .id(provider.getProviderId())
                    .name(provider.getProviderName())
                    .baseUrl(provider.getDefaultBaseUrl())
                    .baseUrlRequired(provider.requiresBaseUrl())
                    .models(provider.getSupportedModels())
                    .enabled(provider.isEnabled())
                    .build());
        }
        return catalogs;
    }
    
    public ProviderCatalog getProviderCatalog(String providerId) {
        AiChatProvider provider = getProvider(providerId);
        return ProviderCatalog.builder()
                .id(provider.getProviderId())
                .name(provider.getProviderName())
                .baseUrl(provider.getDefaultBaseUrl())
                .baseUrlRequired(provider.requiresBaseUrl())
                .models(provider.getSupportedModels())
                .enabled(provider.isEnabled())
                .build();
    }
    
    private List<AiChatProvider> getSortedProviders() {
        return providerMap.values().stream()
                .sorted(Comparator
                        .comparingInt((AiChatProvider provider) -> providerOrderIndex(provider.getProviderId()))
                        .thenComparing(AiChatProvider::getProviderId))
                .collect(Collectors.toList());
    }
    
    private int providerOrderIndex(String providerId) {
        int index = PROVIDER_ORDER.indexOf(providerId);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }
}
