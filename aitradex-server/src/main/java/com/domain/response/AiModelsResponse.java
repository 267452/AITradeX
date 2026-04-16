package com.domain.response;

import com.ai.model.dto.ProviderCatalog;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiModelsResponse(
        @JsonProperty("current_provider") String currentProvider,
        @JsonProperty("current_model") String currentModel,
        List<ProviderCatalog> providers) {
}
