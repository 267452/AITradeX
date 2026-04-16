package com.repository;

import com.common.exception.BusinessException;
import com.domain.request.ConversationSessionUpsertRequest;
import com.domain.request.KnowledgeBaseUpsertRequest;
import com.domain.request.KnowledgeDocumentCreateRequest;
import com.domain.request.McpMarketUpsertRequest;
import com.domain.request.McpToolUpsertRequest;
import com.domain.request.NotificationChannelUpsertRequest;
import com.domain.request.SkillUpsertRequest;
import com.domain.request.WorkflowDefinitionUpsertRequest;
import com.domain.request.WorkflowGraphEdgeUpsertRequest;
import com.domain.request.WorkflowGraphNodeUpsertRequest;
import com.domain.request.WorkflowGraphUpsertRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminModuleRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminModuleRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getKnowledgeStats() {
        return jdbcTemplate.queryForMap("""
                SELECT
                  COUNT(*)::int AS base_count,
                  COALESCE(SUM(document_count), 0)::int AS document_count,
                  COALESCE(SUM(slice_count), 0)::int AS slice_count,
                  COALESCE(MAX(embedding_model), '--') AS embedding_model
                FROM knowledge_base
                """);
    }

    public List<Map<String, Object>> listKnowledgeBases() {
        return jdbcTemplate.queryForList("""
                SELECT id, name, description, vector_store, embedding_model, status,
                       document_count, slice_count, last_sync_at
                FROM knowledge_base
                ORDER BY updated_at DESC, id DESC
                """);
    }

    public List<Map<String, Object>> listKnowledgeDocuments() {
        return jdbcTemplate.queryForList("""
                SELECT kd.id, kb.name AS knowledge_base_name, kd.file_name, kd.parse_status,
                       kd.chunk_count, kd.page_count, kd.sync_note, kd.last_sync_at
                FROM knowledge_document kd
                JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id
                ORDER BY kd.last_sync_at DESC NULLS LAST, kd.id DESC
                LIMIT 20
                """);
    }

    @Transactional
    public Map<String, Object> createKnowledgeDocument(KnowledgeDocumentCreateRequest request) {
        Long knowledgeBaseId = request.knowledgeBaseId();
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new BusinessException("knowledge_base_id_required");
        }
        String fileName = defaultString(request.fileName());
        if (fileName.isBlank()) {
            throw new BusinessException("file_name_required");
        }

        Integer kbExists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)::int
                FROM knowledge_base
                WHERE id = ?
                """, Integer.class, knowledgeBaseId);
        if (kbExists == null || kbExists == 0) {
            throw new BusinessException(404, "knowledge_base_not_found");
        }

        Integer duplicate = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)::int
                FROM knowledge_document
                WHERE knowledge_base_id = ? AND file_name = ?
                """, Integer.class, knowledgeBaseId, fileName);
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("knowledge_document_already_exists");
        }

        int pageCount = Math.max(0, defaultInt(request.pageCount()));
        int chunkCount = Math.max(0, defaultInt(request.chunkCount()));
        String parseStatus = defaultString(request.parseStatus(), "queued");
        String note = defaultString(request.syncNote());
        String sourcePath = defaultString(request.sourcePath());
        if (!sourcePath.isBlank()) {
            note = note.isBlank() ? "source_path=" + sourcePath : note + " | source_path=" + sourcePath;
        }
        if (Boolean.TRUE.equals(request.triggerParse())) {
            note = note.isBlank() ? "已提交解析任务，等待向量化" : note + " | 已提交解析任务";
            if ("draft".equalsIgnoreCase(parseStatus)) {
                parseStatus = "queued";
            }
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String finalParseStatus = parseStatus;
        String finalNote = note;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO knowledge_document
                    (knowledge_base_id, file_name, parse_status, chunk_count, page_count, sync_note, last_sync_at)
                    VALUES (?, ?, ?, ?, ?, ?, NOW())
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, knowledgeBaseId);
            ps.setString(2, fileName);
            ps.setString(3, finalParseStatus);
            ps.setInt(4, chunkCount);
            ps.setInt(5, pageCount);
            ps.setString(6, finalNote);
            return ps;
        }, keyHolder);

        long documentId = extractGeneratedId(keyHolder);

        jdbcTemplate.update("""
                UPDATE knowledge_base
                SET document_count = COALESCE(document_count, 0) + 1,
                    slice_count = COALESCE(slice_count, 0) + ?,
                    last_sync_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                """, chunkCount, knowledgeBaseId);

        return Map.of(
                "id", documentId,
                "knowledge_base_id", knowledgeBaseId,
                "file_name", fileName,
                "parse_status", finalParseStatus,
                "chunk_count", chunkCount,
                "page_count", pageCount,
                "sync_note", finalNote);
    }

    public Map<String, Object> getKnowledgeDocument(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT kd.id, kd.knowledge_base_id, kd.file_name, kd.parse_status, kd.chunk_count,
                       kd.page_count, kd.sync_note, kd.last_sync_at, kb.name AS knowledge_base_name
                FROM knowledge_document kd
                JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id
                WHERE kd.id = ?
                LIMIT 1
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public void updateKnowledgeDocumentSync(long documentId, String parseStatus, Integer chunkCount, Integer pageCount, String syncNote) {
        Map<String, Object> existing = getKnowledgeDocument(documentId);
        if (existing == null) {
            throw new BusinessException(404, "knowledge_document_not_found");
        }
        long knowledgeBaseId = ((Number) existing.get("knowledge_base_id")).longValue();
        int oldChunkCount = ((Number) existing.get("chunk_count")).intValue();
        int oldPageCount = ((Number) existing.get("page_count")).intValue();

        int finalChunkCount = chunkCount == null ? oldChunkCount : Math.max(0, chunkCount);
        int finalPageCount = pageCount == null ? oldPageCount : Math.max(0, pageCount);
        String finalStatus = defaultString(parseStatus, defaultString(String.valueOf(existing.get("parse_status")), "queued"));
        String finalNote = defaultString(syncNote, defaultString(String.valueOf(existing.get("sync_note"))));

        jdbcTemplate.update("""
                UPDATE knowledge_document
                SET parse_status = ?, chunk_count = ?, page_count = ?, sync_note = ?, last_sync_at = NOW()
                WHERE id = ?
                """, finalStatus, finalChunkCount, finalPageCount, finalNote, documentId);

        int diffChunk = finalChunkCount - oldChunkCount;
        if (diffChunk != 0) {
            jdbcTemplate.update("""
                    UPDATE knowledge_base
                    SET slice_count = GREATEST(COALESCE(slice_count, 0) + ?, 0),
                        last_sync_at = NOW(),
                        updated_at = NOW()
                    WHERE id = ?
                    """, diffChunk, knowledgeBaseId);
        } else {
            jdbcTemplate.update("""
                    UPDATE knowledge_base
                    SET last_sync_at = NOW(),
                        updated_at = NOW()
                    WHERE id = ?
                    """, knowledgeBaseId);
        }
    }

    public List<Map<String, Object>> listConversations() {
        return jdbcTemplate.queryForList("""
                SELECT id, session_code, title, channel, model_name, round_count, user_rating,
                       tool_calls, handoff_count, knowledge_hit_rate, status, last_message_at
                FROM conversation_session
                ORDER BY last_message_at DESC, id DESC
                """);
    }

    public Map<String, Object> getConversationInsights() {
        return jdbcTemplate.queryForMap("""
                SELECT
                  COALESCE(ROUND(AVG(handoff_count)::numeric, 2), 0) AS avg_handoff_count,
                  COALESCE(ROUND(AVG(knowledge_hit_rate)::numeric, 2), 0) AS avg_knowledge_hit_rate,
                  COALESCE(SUM(tool_calls), 0)::int AS total_tool_calls,
                  COUNT(*) FILTER (WHERE handoff_count > 0)::int AS risk_sessions
                FROM conversation_session
                """);
    }

    public List<Map<String, Object>> listMcpTools() {
        return jdbcTemplate.queryForList("""
                SELECT id, name, transport_type, endpoint, category, status, last_test_at, note
                FROM mcp_tool
                ORDER BY last_test_at DESC NULLS LAST, id DESC
                """);
    }

    public List<Map<String, Object>> listMcpMarkets() {
        return jdbcTemplate.queryForList("""
                SELECT id, name, package_count, visibility, status, refresh_note, last_refresh_at
                FROM mcp_market
                ORDER BY last_refresh_at DESC NULLS LAST, id DESC
                """);
    }

    public List<Map<String, Object>> listWorkflows() {
        return jdbcTemplate.queryForList("""
                SELECT id, name, description, version_no, status, run_count, category, last_run_at
                FROM workflow_definition
                ORDER BY last_run_at DESC NULLS LAST, id DESC
                """);
    }

    public List<Map<String, Object>> listWorkflowNodes() {
        return jdbcTemplate.queryForList("""
                SELECT wnd.id, wf.name AS workflow_name, wnd.node_name, wnd.node_type,
                       wnd.sort_order, wnd.config_note
                FROM workflow_node_definition wnd
                JOIN workflow_definition wf ON wf.id = wnd.workflow_id
                ORDER BY wf.id ASC, wnd.sort_order ASC, wnd.id ASC
                """);
    }

    public Map<String, Object> getWorkflowGraph(long workflowId) {
        Map<String, Object> workflow = getWorkflowById(workflowId);
        if (workflow == null) {
            throw new BusinessException(404, "workflow_not_found");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, node_name, node_type, sort_order, config_note
                FROM workflow_node_definition
                WHERE workflow_id = ?
                ORDER BY sort_order ASC, id ASC
                """, workflowId);

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> nodeIds = new LinkedHashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            long nodeId = ((Number) row.get("id")).longValue();
            String rawConfig = defaultString(asString(row.get("config_note")));
            Map<String, Object> config = parseJsonMap(rawConfig);

            String clientId = defaultString(
                    asString(config.get("client_id")),
                    defaultString(asString(config.get("clientId")), "node-" + nodeId));
            int x = parseInt(config.get("x"), 120 + (i % 4) * 230);
            int y = parseInt(config.get("y"), 72 + (i / 4) * 138);
            String note = defaultString(
                    asString(config.get("note")),
                    defaultString(asString(config.get("config_note")), rawConfig));

            Map<String, Object> nodePayload = new LinkedHashMap<>();
            nodePayload.put("id", nodeId);
            nodePayload.put("client_id", clientId);
            nodePayload.put("node_name", defaultString(asString(row.get("node_name"))));
            nodePayload.put("node_type", defaultString(asString(row.get("node_type")), "task"));
            nodePayload.put("sort_order", ((Number) row.get("sort_order")).intValue());
            nodePayload.put("x", x);
            nodePayload.put("y", y);
            nodePayload.put("config_note", note);
            nodes.add(nodePayload);
            nodeIds.add(clientId);

            List<String> nextList = extractStringList(config.get("next"));
            Map<String, String> nextLabels = extractStringMap(config.get("next_labels"));
            for (String next : nextList) {
                if (next.isBlank()) {
                    continue;
                }
                Map<String, Object> edgePayload = new LinkedHashMap<>();
                edgePayload.put("id", clientId + "->" + next);
                edgePayload.put("source", clientId);
                edgePayload.put("target", next);
                String label = defaultString(nextLabels.get(next));
                if (!label.isBlank()) {
                    edgePayload.put("label", label);
                }
                edges.add(edgePayload);
            }
        }

        List<Map<String, Object>> normalizedEdges = edges.stream()
                .filter(edge -> nodeIds.contains(defaultString(asString(edge.get("source")))))
                .filter(edge -> nodeIds.contains(defaultString(asString(edge.get("target")))))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflow_id", workflowId);
        payload.put("name", workflow.get("name"));
        payload.put("status", workflow.get("status"));
        payload.put("version_no", workflow.get("version_no"));
        payload.put("nodes", nodes);
        payload.put("edges", normalizedEdges);
        return payload;
    }

    @Transactional
    public void replaceWorkflowGraph(long workflowId, WorkflowGraphUpsertRequest request) {
        Map<String, Object> workflow = getWorkflowById(workflowId);
        if (workflow == null) {
            throw new BusinessException(404, "workflow_not_found");
        }

        List<WorkflowGraphNodeUpsertRequest> nodes = request == null || request.nodes() == null
                ? List.of()
                : request.nodes();
        List<WorkflowGraphEdgeUpsertRequest> edges = request == null || request.edges() == null
                ? List.of()
                : request.edges();

        Set<String> nodeIdSet = new LinkedHashSet<>();
        List<String> orderedNodeIds = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            WorkflowGraphNodeUpsertRequest node = nodes.get(i);
            String clientId = defaultString(node.clientId(), "node-" + (i + 1));
            if (!nodeIdSet.add(clientId)) {
                throw new BusinessException("workflow_node_id_duplicate: " + clientId);
            }
            orderedNodeIds.add(clientId);
        }

        Map<String, List<String>> outgoingMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> outgoingLabelMap = new LinkedHashMap<>();
        for (WorkflowGraphEdgeUpsertRequest edge : edges) {
            String source = defaultString(edge.source());
            String target = defaultString(edge.target());
            if (source.isBlank() || target.isBlank()) {
                continue;
            }
            if (!nodeIdSet.contains(source) || !nodeIdSet.contains(target)) {
                continue;
            }
            List<String> targets = outgoingMap.computeIfAbsent(source, key -> new ArrayList<>());
            if (!targets.contains(target)) {
                targets.add(target);
            }
            String label = defaultString(edge.label());
            if (!label.isBlank()) {
                outgoingLabelMap
                        .computeIfAbsent(source, key -> new LinkedHashMap<>())
                        .put(target, label);
            }
        }

        jdbcTemplate.update("DELETE FROM workflow_node_definition WHERE workflow_id = ?", workflowId);

        for (int i = 0; i < nodes.size(); i++) {
            WorkflowGraphNodeUpsertRequest node = nodes.get(i);
            String clientId = orderedNodeIds.get(i);
            String nodeType = defaultString(node.nodeType(), "task");
            String nodeName = defaultString(node.nodeName(), defaultNodeName(nodeType, i + 1));
            int sortOrder = node.sortOrder() == null ? (i + 1) : Math.max(1, node.sortOrder());
            int x = node.x() == null ? (120 + (i % 4) * 230) : node.x();
            int y = node.y() == null ? (72 + (i / 4) * 138) : node.y();
            String note = defaultString(node.configNote());

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("client_id", clientId);
            config.put("x", x);
            config.put("y", y);
            if (!note.isBlank()) {
                config.put("note", note);
            }
            config.put("next", outgoingMap.getOrDefault(clientId, List.of()));
            Map<String, String> labels = outgoingLabelMap.get(clientId);
            if (labels != null && !labels.isEmpty()) {
                config.put("next_labels", labels);
            }

            jdbcTemplate.update("""
                    INSERT INTO workflow_node_definition (workflow_id, node_name, node_type, sort_order, config_note)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    workflowId,
                    nodeName,
                    nodeType,
                    sortOrder,
                    toJson(config));
        }

        jdbcTemplate.update("""
                UPDATE workflow_definition
                SET updated_at = NOW()
                WHERE id = ?
                """, workflowId);
    }

    public void createKnowledgeBase(KnowledgeBaseUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_base (name, description, vector_store, embedding_model, status, document_count, slice_count, last_sync_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                request.name(),
                defaultString(request.description()),
                defaultString(request.vectorStore(), "Milvus"),
                defaultString(request.embeddingModel(), "bge-m3"),
                defaultString(request.status(), "draft"),
                defaultInt(request.documentCount()),
                defaultInt(request.sliceCount()));
    }

    public void updateKnowledgeBase(long id, KnowledgeBaseUpsertRequest request) {
        jdbcTemplate.update("""
                UPDATE knowledge_base
                SET name = ?, description = ?, vector_store = ?, embedding_model = ?, status = ?,
                    document_count = ?, slice_count = ?, updated_at = NOW()
                WHERE id = ?
                """,
                request.name(),
                defaultString(request.description()),
                defaultString(request.vectorStore(), "Milvus"),
                defaultString(request.embeddingModel(), "bge-m3"),
                defaultString(request.status(), "draft"),
                defaultInt(request.documentCount()),
                defaultInt(request.sliceCount()),
                id);
    }

    public void deleteKnowledgeBase(long id) {
        jdbcTemplate.update("DELETE FROM knowledge_base WHERE id = ?", id);
    }

    public void createConversation(ConversationSessionUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO conversation_session (
                  session_code, title, channel, model_name, round_count, user_rating, tool_calls,
                  handoff_count, knowledge_hit_rate, status, last_message_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """,
                request.sessionCode(),
                request.title(),
                defaultString(request.channel(), "Web"),
                request.modelName(),
                defaultInt(request.roundCount()),
                request.userRating(),
                defaultInt(request.toolCalls()),
                defaultInt(request.handoffCount()),
                request.knowledgeHitRate(),
                defaultString(request.status(), "active"));
    }

    public void updateConversation(long id, ConversationSessionUpsertRequest request) {
        jdbcTemplate.update("""
                UPDATE conversation_session
                SET session_code = ?, title = ?, channel = ?, model_name = ?, round_count = ?,
                    user_rating = ?, tool_calls = ?, handoff_count = ?, knowledge_hit_rate = ?,
                    status = ?, last_message_at = NOW()
                WHERE id = ?
                """,
                request.sessionCode(),
                request.title(),
                defaultString(request.channel(), "Web"),
                request.modelName(),
                defaultInt(request.roundCount()),
                request.userRating(),
                defaultInt(request.toolCalls()),
                defaultInt(request.handoffCount()),
                request.knowledgeHitRate(),
                defaultString(request.status(), "active"),
                id);
    }

    public void deleteConversation(long id) {
        jdbcTemplate.update("DELETE FROM conversation_session WHERE id = ?", id);
    }

    public void createMcpTool(McpToolUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO mcp_tool (name, transport_type, endpoint, category, status, last_test_at, note)
                VALUES (?, ?, ?, ?, ?, NOW(), ?)
                """,
                request.name(),
                request.transportType(),
                request.endpoint(),
                defaultString(request.category(), "general"),
                defaultString(request.status(), "enabled"),
                defaultString(request.note()));
    }

    public void updateMcpTool(long id, McpToolUpsertRequest request) {
        jdbcTemplate.update("""
                UPDATE mcp_tool
                SET name = ?, transport_type = ?, endpoint = ?, category = ?, status = ?, note = ?, last_test_at = NOW()
                WHERE id = ?
                """,
                request.name(),
                request.transportType(),
                request.endpoint(),
                defaultString(request.category(), "general"),
                defaultString(request.status(), "enabled"),
                defaultString(request.note()),
                id);
    }

    public void deleteMcpTool(long id) {
        jdbcTemplate.update("DELETE FROM mcp_tool WHERE id = ?", id);
    }

    public List<Map<String, Object>> listSkills() {
        return jdbcTemplate.queryForList("""
                SELECT id, name, description, icon, category, status, prompt_template,
                       variables, tools, enabled_tools, run_count, last_run_at
                FROM skill
                ORDER BY last_run_at DESC NULLS LAST, id DESC
                """);
    }

    public Map<String, Object> getSkill(long id) {
        return jdbcTemplate.queryForMap("""
                SELECT id, name, description, icon, category, status, prompt_template,
                       variables, tools, enabled_tools, run_count, last_run_at
                FROM skill WHERE id = ?
                """, id);
    }

    public void createSkill(SkillUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO skill (name, description, icon, category, status, prompt_template,
                                   variables, tools, enabled_tools, last_run_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, NOW())
                """,
                request.name(),
                defaultString(request.description()),
                defaultString(request.icon(), "⚡"),
                defaultString(request.category(), "general"),
                defaultString(request.status(), "enabled"),
                defaultString(request.promptTemplate()),
                toJsonString(request.variables()),
                toJsonString(request.tools()),
                defaultString(request.enabledTools()));
    }

    public void updateSkill(long id, SkillUpsertRequest request) {
        jdbcTemplate.update("""
                UPDATE skill
                SET name = ?, description = ?, icon = ?, category = ?, status = ?,
                    prompt_template = ?, variables = ?::jsonb, tools = ?::jsonb,
                    enabled_tools = ?, last_run_at = NOW()
                WHERE id = ?
                """,
                request.name(),
                defaultString(request.description()),
                defaultString(request.icon(), "⚡"),
                defaultString(request.category(), "general"),
                defaultString(request.status(), "enabled"),
                defaultString(request.promptTemplate()),
                toJsonString(request.variables()),
                toJsonString(request.tools()),
                defaultString(request.enabledTools()),
                id);
    }

    public void deleteSkill(long id) {
        jdbcTemplate.update("DELETE FROM skill WHERE id = ?", id);
    }

    public List<Map<String, Object>> listNotificationChannels() {
        return jdbcTemplate.queryForList("""
                SELECT id, name, channel_type, config, enabled, created_at, updated_at
                FROM notification_channel
                ORDER BY id ASC
                """);
    }

    public Map<String, Object> getNotificationChannel(long id) {
        return jdbcTemplate.queryForMap("""
                SELECT id, name, channel_type, config, enabled, created_at, updated_at
                FROM notification_channel WHERE id = ?
                """, id);
    }

    public void createNotificationChannel(NotificationChannelUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO notification_channel (name, channel_type, config, enabled)
                VALUES (?, ?, ?::jsonb, ?)
                """,
                request.name(),
                request.channelType(),
                toJson(request.config()),
                request.enabled() != null ? request.enabled() : true);
    }

    public void updateNotificationChannel(long id, NotificationChannelUpsertRequest request) {
        jdbcTemplate.update("""
                UPDATE notification_channel
                SET name = ?, channel_type = ?, config = ?::jsonb, enabled = ?, updated_at = NOW()
                WHERE id = ?
                """,
                request.name(),
                request.channelType(),
                toJson(request.config()),
                request.enabled() != null ? request.enabled() : true,
                id);
    }

    public void deleteNotificationChannel(long id) {
        jdbcTemplate.update("DELETE FROM notification_channel WHERE id = ?", id);
    }

    public void createMcpMarket(McpMarketUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO mcp_market (name, package_count, visibility, status, refresh_note, last_refresh_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """,
                request.name(),
                defaultInt(request.packageCount()),
                defaultString(request.visibility(), "public"),
                defaultString(request.status(), "online"),
                defaultString(request.refreshNote()));
    }

    public void updateMcpMarket(long id, McpMarketUpsertRequest request) {
        jdbcTemplate.update("""
                UPDATE mcp_market
                SET name = ?, package_count = ?, visibility = ?, status = ?, refresh_note = ?, last_refresh_at = NOW()
                WHERE id = ?
                """,
                request.name(),
                defaultInt(request.packageCount()),
                defaultString(request.visibility(), "public"),
                defaultString(request.status(), "online"),
                defaultString(request.refreshNote()),
                id);
    }

    public void deleteMcpMarket(long id) {
        jdbcTemplate.update("DELETE FROM mcp_market WHERE id = ?", id);
    }

    public void createWorkflow(WorkflowDefinitionUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO workflow_definition (name, description, version_no, status, run_count, category, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """,
                request.name(),
                defaultString(request.description()),
                request.versionNo() == null ? 1 : request.versionNo(),
                defaultString(request.status(), "draft"),
                defaultInt(request.runCount()),
                defaultString(request.category(), "general"));
    }

    public void updateWorkflow(long id, WorkflowDefinitionUpsertRequest request) {
        jdbcTemplate.update("""
                UPDATE workflow_definition
                SET name = ?, description = ?, version_no = ?, status = ?, run_count = ?, category = ?, updated_at = NOW()
                WHERE id = ?
                """,
                request.name(),
                defaultString(request.description()),
                request.versionNo() == null ? 1 : request.versionNo(),
                defaultString(request.status(), "draft"),
                defaultInt(request.runCount()),
                defaultString(request.category(), "general"),
                id);
    }

    public void deleteWorkflow(long id) {
        jdbcTemplate.update("DELETE FROM workflow_definition WHERE id = ?", id);
    }

    private Map<String, Object> getWorkflowById(long workflowId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, name, status, version_no
                FROM workflow_definition
                WHERE id = ?
                LIMIT 1
                """, workflowId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> parseJsonMap(String value) {
        String text = defaultString(value);
        if (text.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "workflow_graph_json_serialize_failed");
        }
    }

    private String toJsonString(String[] array) {
        if (array == null || array.length == 0) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(array);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "skill_json_serialize_failed");
        }
    }

    private List<String> extractStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, String> extractStringMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        rawMap.forEach((key, val) -> {
            String k = defaultString(String.valueOf(key));
            String v = defaultString(val == null ? "" : String.valueOf(val));
            if (!k.isBlank() && !v.isBlank()) {
                result.put(k, v);
            }
        });
        return result;
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String defaultNodeName(String nodeType, int index) {
        return switch (defaultString(nodeType)) {
            case "start" -> "开始节点";
            case "end" -> "结束节点";
            case "condition" -> "条件判断";
            case "mcp_tool" -> "MCP 工具";
            case "llm" -> "模型节点";
            default -> "节点-" + index;
        };
    }

    private String defaultString(String value) {
        return defaultString(value, "");
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long extractGeneratedId(KeyHolder keyHolder) {
        try {
            Number id = keyHolder.getKey();
            if (id != null) {
                return id.longValue();
            }
        } catch (InvalidDataAccessApiUsageException ignore) {
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object idCandidate = keys.get("id");
            if (!(idCandidate instanceof Number) && !keys.isEmpty()) {
                idCandidate = new LinkedHashMap<>(keys).values().iterator().next();
            }
            if (idCandidate instanceof Number number) {
                return number.longValue();
            }
        }

        List<Map<String, Object>> keyList = keyHolder.getKeyList();
        if (!keyList.isEmpty()) {
            Map<String, Object> first = keyList.get(0);
            if (first != null) {
                Object idCandidate = first.get("id");
                if (!(idCandidate instanceof Number) && !first.isEmpty()) {
                    idCandidate = first.values().iterator().next();
                }
                if (idCandidate instanceof Number number) {
                    return number.longValue();
                }
            }
        }
        return 0L;
    }
}
