package com.controller.admin;

import com.common.api.ApiResponse;
import com.domain.request.ConversationSessionUpsertRequest;
import com.domain.request.KnowledgeBaseUpsertRequest;
import com.domain.request.KnowledgeDocumentCreateRequest;
import com.domain.request.McpMarketUpsertRequest;
import com.domain.request.McpToolUpsertRequest;
import com.domain.request.NotificationChannelUpsertRequest;
import com.domain.request.SkillUpsertRequest;
import com.domain.request.WorkflowDefinitionUpsertRequest;
import com.domain.request.WorkflowGraphUpsertRequest;
import com.repository.AdminModuleRepository;
import com.service.KnowledgeDocumentService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminModuleController {
    private final AdminModuleRepository adminModuleRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;

    public AdminModuleController(
            AdminModuleRepository adminModuleRepository,
            KnowledgeDocumentService knowledgeDocumentService) {
        this.adminModuleRepository = adminModuleRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @GetMapping("/knowledge/stats")
    public ApiResponse<Map<String, Object>> knowledgeStats() {
        return ApiResponse.success(adminModuleRepository.getKnowledgeStats());
    }

    @PostMapping("/knowledge/bases")
    public ApiResponse<Void> createKnowledgeBase(@RequestBody KnowledgeBaseUpsertRequest request) {
        adminModuleRepository.createKnowledgeBase(request);
        return ApiResponse.success();
    }

    @PutMapping("/knowledge/bases/{id}")
    public ApiResponse<Void> updateKnowledgeBase(@PathVariable long id, @RequestBody KnowledgeBaseUpsertRequest request) {
        adminModuleRepository.updateKnowledgeBase(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/knowledge/bases/{id}")
    public ApiResponse<Void> deleteKnowledgeBase(@PathVariable long id) {
        adminModuleRepository.deleteKnowledgeBase(id);
        return ApiResponse.success();
    }

    @GetMapping("/knowledge/bases")
    public ApiResponse<List<Map<String, Object>>> knowledgeBases() {
        return ApiResponse.success(adminModuleRepository.listKnowledgeBases());
    }

    @GetMapping("/knowledge/documents")
    public ApiResponse<List<Map<String, Object>>> knowledgeDocuments() {
        return ApiResponse.success(adminModuleRepository.listKnowledgeDocuments());
    }

    @PostMapping("/knowledge/documents")
    public ApiResponse<Map<String, Object>> createKnowledgeDocument(@RequestBody KnowledgeDocumentCreateRequest request) {
        return ApiResponse.success(knowledgeDocumentService.createDocument(request));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<Map<String, Object>>> conversations() {
        return ApiResponse.success(adminModuleRepository.listConversations());
    }

    @PostMapping("/conversations")
    public ApiResponse<Void> createConversation(@RequestBody ConversationSessionUpsertRequest request) {
        adminModuleRepository.createConversation(request);
        return ApiResponse.success();
    }

    @PutMapping("/conversations/{id}")
    public ApiResponse<Void> updateConversation(@PathVariable long id, @RequestBody ConversationSessionUpsertRequest request) {
        adminModuleRepository.updateConversation(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable long id) {
        adminModuleRepository.deleteConversation(id);
        return ApiResponse.success();
    }

    @GetMapping("/conversations/insights")
    public ApiResponse<Map<String, Object>> conversationInsights() {
        return ApiResponse.success(adminModuleRepository.getConversationInsights());
    }

    @GetMapping("/mcp/tools")
    public ApiResponse<List<Map<String, Object>>> mcpTools() {
        return ApiResponse.success(adminModuleRepository.listMcpTools());
    }

    @PostMapping("/mcp/tools")
    public ApiResponse<Void> createMcpTool(@RequestBody McpToolUpsertRequest request) {
        adminModuleRepository.createMcpTool(request);
        return ApiResponse.success();
    }

    @PutMapping("/mcp/tools/{id}")
    public ApiResponse<Void> updateMcpTool(@PathVariable long id, @RequestBody McpToolUpsertRequest request) {
        adminModuleRepository.updateMcpTool(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/mcp/tools/{id}")
    public ApiResponse<Void> deleteMcpTool(@PathVariable long id) {
        adminModuleRepository.deleteMcpTool(id);
        return ApiResponse.success();
    }

    @GetMapping("/mcp/markets")
    public ApiResponse<List<Map<String, Object>>> mcpMarkets() {
        return ApiResponse.success(adminModuleRepository.listMcpMarkets());
    }

    @PostMapping("/mcp/markets")
    public ApiResponse<Void> createMcpMarket(@RequestBody McpMarketUpsertRequest request) {
        adminModuleRepository.createMcpMarket(request);
        return ApiResponse.success();
    }

    @PutMapping("/mcp/markets/{id}")
    public ApiResponse<Void> updateMcpMarket(@PathVariable long id, @RequestBody McpMarketUpsertRequest request) {
        adminModuleRepository.updateMcpMarket(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/mcp/markets/{id}")
    public ApiResponse<Void> deleteMcpMarket(@PathVariable long id) {
        adminModuleRepository.deleteMcpMarket(id);
        return ApiResponse.success();
    }

    @GetMapping("/workflows")
    public ApiResponse<List<Map<String, Object>>> workflows() {
        return ApiResponse.success(adminModuleRepository.listWorkflows());
    }

    @PostMapping("/workflows")
    public ApiResponse<Void> createWorkflow(@RequestBody WorkflowDefinitionUpsertRequest request) {
        adminModuleRepository.createWorkflow(request);
        return ApiResponse.success();
    }

    @PutMapping("/workflows/{id}")
    public ApiResponse<Void> updateWorkflow(@PathVariable long id, @RequestBody WorkflowDefinitionUpsertRequest request) {
        adminModuleRepository.updateWorkflow(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/workflows/{id}")
    public ApiResponse<Void> deleteWorkflow(@PathVariable long id) {
        adminModuleRepository.deleteWorkflow(id);
        return ApiResponse.success();
    }

    @GetMapping("/workflows/nodes")
    public ApiResponse<List<Map<String, Object>>> workflowNodes() {
        return ApiResponse.success(adminModuleRepository.listWorkflowNodes());
    }

    @GetMapping("/workflows/{id}/graph")
    public ApiResponse<Map<String, Object>> workflowGraph(@PathVariable long id) {
        return ApiResponse.success(adminModuleRepository.getWorkflowGraph(id));
    }

    @PutMapping("/workflows/{id}/graph")
    public ApiResponse<Void> updateWorkflowGraph(@PathVariable long id, @RequestBody WorkflowGraphUpsertRequest request) {
        adminModuleRepository.replaceWorkflowGraph(id, request);
        return ApiResponse.success();
    }

    @GetMapping("/skills")
    public ApiResponse<List<Map<String, Object>>> skills() {
        return ApiResponse.success(adminModuleRepository.listSkills());
    }

    @GetMapping("/skills/{id}")
    public ApiResponse<Map<String, Object>> skill(@PathVariable long id) {
        return ApiResponse.success(adminModuleRepository.getSkill(id));
    }

    @PostMapping("/skills")
    public ApiResponse<Void> createSkill(@RequestBody SkillUpsertRequest request) {
        adminModuleRepository.createSkill(request);
        return ApiResponse.success();
    }

    @PutMapping("/skills/{id}")
    public ApiResponse<Void> updateSkill(@PathVariable long id, @RequestBody SkillUpsertRequest request) {
        adminModuleRepository.updateSkill(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/skills/{id}")
    public ApiResponse<Void> deleteSkill(@PathVariable long id) {
        adminModuleRepository.deleteSkill(id);
        return ApiResponse.success();
    }

    @GetMapping("/notification-channels")
    public ApiResponse<List<Map<String, Object>>> notificationChannels() {
        return ApiResponse.success(adminModuleRepository.listNotificationChannels());
    }

    @GetMapping("/notification-channels/{id}")
    public ApiResponse<Map<String, Object>> notificationChannel(@PathVariable long id) {
        return ApiResponse.success(adminModuleRepository.getNotificationChannel(id));
    }

    @PostMapping("/notification-channels")
    public ApiResponse<Void> createNotificationChannel(@RequestBody NotificationChannelUpsertRequest request) {
        adminModuleRepository.createNotificationChannel(request);
        return ApiResponse.success();
    }

    @PutMapping("/notification-channels/{id}")
    public ApiResponse<Void> updateNotificationChannel(@PathVariable long id, @RequestBody NotificationChannelUpsertRequest request) {
        adminModuleRepository.updateNotificationChannel(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/notification-channels/{id}")
    public ApiResponse<Void> deleteNotificationChannel(@PathVariable long id) {
        adminModuleRepository.deleteNotificationChannel(id);
        return ApiResponse.success();
    }
}
