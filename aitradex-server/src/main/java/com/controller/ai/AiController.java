package com.controller.ai;

import com.ai.service.FinancialAgentService;
import com.common.api.ApiResponse;
import com.domain.request.AiChatRequest;
import com.domain.request.AiConfirmExecuteRequest;
import com.domain.request.AiConfigRequest;
import com.domain.request.AiModelSwitchRequest;
import com.domain.response.AiConfigResponse;
import com.domain.response.AiModelsResponse;
import com.domain.response.AiOperationResponse;
import com.domain.response.AiSavedConfigResponse;
import com.domain.response.AiSwitchModelResponse;
import com.domain.response.AiTestResponse;
import com.service.AIService;
import com.service.ExecutionApprovalService;
import com.service.ExecutionApprovalService.ApprovalCheckResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AIService aiService;
    private final FinancialAgentService financialAgentService;
    private final ExecutionApprovalService executionApprovalService;

    public AiController(AIService aiService,
                        FinancialAgentService financialAgentService,
                        ExecutionApprovalService executionApprovalService) {
        this.aiService = aiService;
        this.financialAgentService = financialAgentService;
        this.executionApprovalService = executionApprovalService;
    }

    @GetMapping("/models")
    public ApiResponse<AiModelsResponse> listAiModels() {
        return ApiResponse.success(aiService.listModels());
    }

    @GetMapping("/config")
    public ApiResponse<AiConfigResponse> getAiConfig() {
        return ApiResponse.success(aiService.getConfig());
    }

    @GetMapping("/saved-configs")
    public ApiResponse<List<AiSavedConfigResponse>> listSavedConfigs() {
        return ApiResponse.success(aiService.listSavedConfigs());
    }

    @PostMapping("/config")
    public ApiResponse<AiOperationResponse> saveAiConfig(@RequestBody AiConfigRequest config) {
        return ApiResponse.success(aiService.saveConfig(config));
    }

    @DeleteMapping("/config")
    public ApiResponse<AiOperationResponse> deleteAiConfig() {
        return ApiResponse.success(aiService.clearConfig());
    }

    @PostMapping("/test")
    public ApiResponse<AiTestResponse> testAiConnection(@RequestBody AiConfigRequest config) {
        return ApiResponse.success(aiService.testConnection(config));
    }

    @PostMapping("/switch-model")
    public ApiResponse<AiSwitchModelResponse> switchAiModel(@RequestBody AiModelSwitchRequest request) {
        if (request.model() == null || request.model().isBlank()) {
            return ApiResponse.success(new AiSwitchModelResponse(false, "模型名称不能为空", null, null, null));
        }
        return ApiResponse.success(aiService.switchModel(request.provider(), request.model(), request.modelId()));
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> aiChat(@RequestBody AiChatRequest request) {
        if (request.message() == null || request.message().trim().isEmpty()) {
            return ApiResponse.success(Map.of("success", false, "message", "消息不能为空"));
        }
        return ApiResponse.success(financialAgentService.handle(
                request.message(),
                request.provider(),
                request.model(),
                request.conversationId(),
                request.workflowId(),
                false));
    }

    @PostMapping("/simple-chat")
    public ApiResponse<Map<String, Object>> simpleChat(@RequestBody AiChatRequest request) {
        if (request.message() == null || request.message().trim().isEmpty()) {
            return ApiResponse.success(Map.of("success", false, "message", "消息不能为空"));
        }
        return ApiResponse.success(aiService.simpleChat(request.message()));
    }

    @PostMapping("/chat-and-execute")
    public ApiResponse<Map<String, Object>> aiChatAndExecute(@RequestBody AiChatRequest request) {
        if (request.message() == null || request.message().trim().isEmpty()) {
            return ApiResponse.success(Map.of("success", false, "message", "消息不能为空"));
        }
        if (executionApprovalService.isLiveBrokerMode()) {
            return ApiResponse.success(Map.of(
                    "success", false,
                    "approval_required", true,
                    "message", "实盘模式已启用审批，禁止直接执行。请先分析，再通过确认执行提交审批口令。"));
        }
        return ApiResponse.success(financialAgentService.handle(
                request.message(),
                request.provider(),
                request.model(),
                request.conversationId(),
                request.workflowId(),
                true));
    }

    @PostMapping("/confirm-execute")
    public ApiResponse<Map<String, Object>> confirmExecute(@RequestBody AiConfirmExecuteRequest request,
                                                           HttpServletRequest httpRequest) {
        if (request.command() == null || request.command().trim().isEmpty()) {
            return ApiResponse.success(Map.of("success", false, "message", "确认执行缺少 command"));
        }
        Object usernameAttr = httpRequest.getAttribute("username");
        String operator = usernameAttr == null ? "" : String.valueOf(usernameAttr);
        ApprovalCheckResult approval = executionApprovalService.validateLiveConfirm(request, operator);
        if (!approval.approved()) {
            Map<String, Object> denied = new LinkedHashMap<>();
            denied.put("success", false);
            denied.put("approval_required", true);
            denied.put("message", approval.message());
            denied.put("execution_approval", approval.context());
            return ApiResponse.success(denied);
        }
        Map<String, Object> result = financialAgentService.handle(
                request.command(),
                request.provider(),
                request.model(),
                request.conversationId(),
                request.workflowId(),
                true,
                Map.of("execution_approval", approval.context()));
        if (!result.containsKey("execution_approval")) {
            Map<String, Object> output = new LinkedHashMap<>(result);
            output.put("execution_approval", approval.context());
            return ApiResponse.success(output);
        }
        return ApiResponse.success(result);
    }
}
