package com.controller.admin;

import com.common.api.ApiResponse;
import com.common.exception.BusinessException;
import com.domain.request.ConversationSessionUpsertRequest;
import com.domain.request.KnowledgeBaseUpsertRequest;
import com.domain.request.KnowledgeDocumentCreateRequest;
import com.domain.request.McpMarketUpsertRequest;
import com.domain.request.McpToolUpsertRequest;
import com.domain.request.NotificationChannelUpsertRequest;
import com.domain.request.SkillUpsertRequest;
import com.domain.request.WorkflowDefinitionUpsertRequest;
import com.domain.request.WorkflowGraphUpsertRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.AdminModuleRepository;
import com.service.KnowledgeDocumentService;
import com.service.SkillService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/admin")
public class AdminModuleController {
    private static final Logger log = LoggerFactory.getLogger(AdminModuleController.class);
    private final AdminModuleRepository adminModuleRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    public AdminModuleController(
            AdminModuleRepository adminModuleRepository,
            KnowledgeDocumentService knowledgeDocumentService,
            SkillService skillService,
            ObjectMapper objectMapper) {
        this.adminModuleRepository = adminModuleRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.skillService = skillService;
        this.objectMapper = objectMapper;
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

    @GetMapping("/knowledge/documents/{id}")
    public ApiResponse<Map<String, Object>> getKnowledgeDocument(@PathVariable long id) {
        Map<String, Object> document = adminModuleRepository.getKnowledgeDocument(id);
        if (document == null) {
            return ApiResponse.error("文档不存在");
        }
        return ApiResponse.success(document);
    }

    @PostMapping("/knowledge/documents")
    public ApiResponse<Map<String, Object>> createKnowledgeDocument(@RequestBody KnowledgeDocumentCreateRequest request) {
        return ApiResponse.success(knowledgeDocumentService.createDocument(request));
    }

    @PutMapping("/knowledge/documents/{id}")
    public ApiResponse<Map<String, Object>> updateKnowledgeDocument(
            @PathVariable long id,
            @RequestBody KnowledgeDocumentCreateRequest request) {
        return ApiResponse.success(adminModuleRepository.updateKnowledgeDocument(id, request));
    }

    @DeleteMapping("/knowledge/documents/{id}")
    public ApiResponse<Map<String, Object>> deleteKnowledgeDocument(@PathVariable long id) {
        Map<String, Object> deleted = adminModuleRepository.deleteKnowledgeDocument(id);
        if (deleted == null) {
            return ApiResponse.error("文档不存在");
        }
        return ApiResponse.success(deleted);
    }

    @GetMapping("/knowledge/documents/{id}/content")
    public ApiResponse<Map<String, Object>> getKnowledgeDocumentContent(@PathVariable long id) throws IOException {
        Map<String, Object> doc = adminModuleRepository.getKnowledgeDocument(id);
        if (doc == null) {
            return ApiResponse.error("文档不存在");
        }

        String fileName = String.valueOf(doc.get("file_name"));
        String sourcePath = doc.get("source_path") != null ? String.valueOf(doc.get("source_path")) : "";

        // 根据文件扩展名尝试读取
        String lowerName = fileName.toLowerCase();
        boolean isTextFormat = lowerName.endsWith(".txt") || lowerName.endsWith(".md") ||
                              lowerName.endsWith(".markdown") || lowerName.endsWith(".csv") ||
                              lowerName.endsWith(".json") || lowerName.endsWith(".xml") ||
                              lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") ||
                              lowerName.endsWith(".html") || lowerName.endsWith(".htm") ||
                              lowerName.endsWith(".log");

        Map<String, Object> result = new HashMap<>();
        result.put("id", doc.get("id"));
        result.put("file_name", fileName);
        result.put("source_path", sourcePath);
        result.put("parse_status", doc.get("parse_status"));
        result.put("file_format", lowerName.contains(".") ? lowerName.substring(lowerName.lastIndexOf(".") + 1) : "unknown");
        result.put("is_text", isTextFormat);

        // 如果有 source_path 并且文件存在，尝试读取内容
        if (sourcePath != null && !sourcePath.isBlank()) {
            Path filePath = Paths.get(sourcePath).toAbsolutePath().normalize();
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                long fileSize = Files.size(filePath);
                result.put("file_size", fileSize);

                if (isTextFormat) {
                    // 限制读取最大 1MB 的文本内容
                    long maxBytes = 1024 * 1024;
                    if (fileSize <= maxBytes) {
                        String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                        result.put("content", content);
                        result.put("content_truncated", false);
                    } else {
                        // 大文件只读取前 1MB
                        byte[] bytes = new byte[(int) maxBytes];
                        try (var in = Files.newInputStream(filePath)) {
                            int read = in.read(bytes);
                            if (read > 0) {
                                result.put("content", new String(bytes, 0, read, StandardCharsets.UTF_8));
                            } else {
                                result.put("content", "");
                            }
                        }
                        result.put("content_truncated", true);
                    }
                } else {
                    result.put("content", "二进制或非文本文件（PDF/Word/Excel/PPT 等），请下载后用对应工具打开。");
                    result.put("content_truncated", true);
                }
            } else {
                result.put("file_size", 0);
                result.put("content", "文件不存在或路径无效: " + sourcePath);
                result.put("content_truncated", false);
            }
        } else {
            result.put("file_size", 0);
            result.put("content", "该文档没有保存文件路径，仅用于文档记录（非上传文件创建）。");
            result.put("content_truncated", false);
        }

        return ApiResponse.success(result);
    }

    @PostMapping("/knowledge/documents/upload")
    public ApiResponse<Map<String, Object>> uploadKnowledgeDocuments(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "knowledge_base_name", required = false) String knowledgeBaseName,
            @RequestParam(value = "knowledge_base_description", required = false) String knowledgeBaseDescription,
            @RequestParam(value = "knowledge_base_id", required = false) Long knowledgeBaseId) throws IOException {
        
        // 如果没有提供知识库ID，先创建知识库
        Long baseId = knowledgeBaseId;
        if (baseId == null || baseId <= 0) {
            String name = knowledgeBaseName != null && !knowledgeBaseName.isBlank()
                    ? knowledgeBaseName
                    : "知识库-" + UUID.randomUUID().toString().substring(0, 8);

            KnowledgeBaseUpsertRequest baseRequest = new KnowledgeBaseUpsertRequest(
                    name,
                    knowledgeBaseDescription != null ? knowledgeBaseDescription : "",
                    "Milvus",
                    "bge-m3",
                    "online",
                    0,
                    0,
                    null
            );
            adminModuleRepository.createKnowledgeBase(baseRequest);
            
            // 获取刚创建的知识库ID
            List<Map<String, Object>> bases = adminModuleRepository.listKnowledgeBases();
            if (bases != null && !bases.isEmpty()) {
                baseId = asLong(bases.get(bases.size() - 1).get("id"));
            }
        }
        
        if (baseId == null || baseId <= 0) {
            return ApiResponse.error("无法创建或找到知识库");
        }
        
        // 创建上传目录（自动递归创建，不存在则创建）
        Path uploadDir = Paths.get("uploads", "knowledge", String.valueOf(baseId)).toAbsolutePath();
        Files.createDirectories(uploadDir);
        log.info("上传目录: {}", uploadDir);
        
        int successCount = 0;
        int failCount = 0;
        List<Map<String, Object>> uploadedDocuments = new java.util.ArrayList<>();
        
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                failCount++;
                continue;
            }
            
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = "unknown-file-" + UUID.randomUUID().toString().substring(0, 8);
            }
            
            // 保存文件
            String uniqueFilename = UUID.randomUUID().toString() + "-" + originalFilename;
            Path filePath = uploadDir.resolve(uniqueFilename);
            file.transferTo(filePath.toFile());
            log.info("文件已保存: {}", filePath);
            
            // 创建文档记录
            KnowledgeDocumentCreateRequest docRequest = new KnowledgeDocumentCreateRequest(
                    baseId,
                    originalFilename,
                    "syncing",
                    null,
                    null,
                    null,
                    filePath.toString(),
                    true  // trigger_parse
            );
            
            try {
                Map<String, Object> created = knowledgeDocumentService.createDocument(docRequest);
                uploadedDocuments.add(created);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("创建文档记录失败: {}", e.getMessage());
                Files.deleteIfExists(filePath);
            }
        }
        
        log.info("上传完成: 成功={}, 失败={}, 目录={}", successCount, failCount, uploadDir);
        
        Map<String, Object> result = new HashMap<>();
        result.put("knowledge_base_id", baseId);
        result.put("success_count", successCount);
        result.put("fail_count", failCount);
        result.put("documents", uploadedDocuments);
        result.put("upload_dir", uploadDir.toString());
        
        return ApiResponse.success(result);
    }
    
    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
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
        return ApiResponse.success(skillService.listSkills());
    }

    @GetMapping("/skills/{id}")
    public ApiResponse<Map<String, Object>> skill(@PathVariable long id) {
        return ApiResponse.success(skillService.getSkill(id));
    }

    @GetMapping("/skills/{id}/detail")
    public ApiResponse<Map<String, Object>> skillDetail(@PathVariable long id) {
        return ApiResponse.success(skillService.getSkillDetail(id));
    }

    @PostMapping("/skills")
    public ApiResponse<Map<String, Object>> createSkill(@RequestBody SkillUpsertRequest request) {
        long id = skillService.createSkill(request);
        return ApiResponse.success(Map.of("id", id));
    }

    @PutMapping("/skills/{id}")
    public ApiResponse<Void> updateSkill(@PathVariable long id, @RequestBody SkillUpsertRequest request) {
        skillService.updateSkill(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/skills/{id}")
    public ApiResponse<Void> deleteSkill(@PathVariable long id) {
        skillService.deleteSkill(id);
        return ApiResponse.success();
    }

    @PostMapping("/skills/upload")
    public ApiResponse<Map<String, Object>> uploadSkill(
            @RequestParam("skillPackage") MultipartFile zipFile) throws IOException {

        if (zipFile == null || zipFile.isEmpty()) {
            throw new BusinessException(400, "zip_file_empty");
        }

        String configJson = null;
        String promptContent = "";
        String scriptContent = "";

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                if ("config.json".equalsIgnoreCase(name)) {
                    configJson = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                } else if ("prompt.md".equalsIgnoreCase(name)) {
                    promptContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                } else if ("script.py".equalsIgnoreCase(name)) {
                    scriptContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                } else {
                    // 忽略其他文件
                }
                zis.closeEntry();
            }
        }

        if (configJson == null || configJson.isBlank()) {
            throw new BusinessException(400, "config_json_required");
        }

        // 解析config.json
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            throw new BusinessException(400, "invalid_config_json");
        }

        String name = config.get("name") != null ? config.get("name").toString() : "未命名技能";
        String description = config.get("description") != null ? config.get("description").toString() : "";
        String icon = config.get("icon") != null ? config.get("icon").toString() : "⚡";
        String category = config.get("category") != null ? config.get("category").toString() : "general";
        String status = config.get("status") != null ? config.get("status").toString() : "enabled";

        SkillUpsertRequest request = new SkillUpsertRequest(
                name,
                description,
                icon,
                category,
                status,
                promptContent,
                null,
                null,
                null
        );

        long skillId = skillService.createSkill(request);

        if (!scriptContent.isEmpty()) {
            skillService.writeScript(skillId, scriptContent);
        }

        return ApiResponse.success(Map.of(
                "id", skillId,
                "name", name,
                "has_prompt", !promptContent.isEmpty(),
                "has_script", !scriptContent.isEmpty()
        ));
    }

    @GetMapping("/skills/{id}/prompt")
    public ApiResponse<String> skillPrompt(@PathVariable long id) {
        return ApiResponse.success(skillService.readPrompt(id));
    }

    @PutMapping("/skills/{id}/prompt")
    public ApiResponse<Void> updateSkillPrompt(@PathVariable long id, @RequestBody String content) {
        skillService.writePrompt(id, content);
        return ApiResponse.success();
    }

    @GetMapping("/skills/{id}/script")
    public ApiResponse<String> skillScript(@PathVariable long id) {
        return ApiResponse.success(skillService.readScript(id));
    }

    @PutMapping("/skills/{id}/script")
    public ApiResponse<Void> updateSkillScript(@PathVariable long id, @RequestBody String content) {
        skillService.writeScript(id, content);
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
