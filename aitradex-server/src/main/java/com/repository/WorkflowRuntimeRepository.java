package com.repository;

import com.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowRuntimeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowRuntimeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long createWorkflowRun(String runId,
                                  Long workflowId,
                                  Long conversationId,
                                  Map<String, Object> inputPayload) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO workflow_run (run_id, workflow_id, conversation_id, status, input_payload, started_at, updated_at)
                    VALUES (?, ?, ?, 'running', ?::jsonb, NOW(), NOW())
                    """, new String[] {"id"});
            ps.setString(1, runId);
            ps.setObject(2, workflowId);
            ps.setObject(3, conversationId);
            ps.setString(4, toJson(inputPayload));
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            throw new BusinessException(500, "workflow_run_create_failed");
        }
        return id.longValue();
    }

    public void appendWorkflowRunStep(long workflowRunId,
                                      int stepOrder,
                                      String nodeId,
                                      String nodeName,
                                      String nodeType,
                                      String status,
                                      Map<String, Object> inputPayload,
                                      Map<String, Object> outputPayload,
                                      String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO workflow_run_step (
                    workflow_run_id, step_order, node_id, node_name, node_type,
                    status, input_payload, output_payload, error_message,
                    started_at, finished_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, NOW(), NOW(), NOW())
                """,
                workflowRunId,
                stepOrder,
                nodeId,
                nodeName,
                nodeType,
                status,
                toJson(inputPayload),
                toJson(outputPayload),
                errorMessage == null ? "" : errorMessage);
    }

    public void completeWorkflowRun(long workflowRunId, Map<String, Object> outputPayload) {
        jdbcTemplate.update("""
                UPDATE workflow_run
                SET status = 'completed',
                    output_payload = ?::jsonb,
                    error_message = '',
                    finished_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                """, toJson(outputPayload), workflowRunId);
    }

    public void failWorkflowRun(long workflowRunId, String errorMessage, Map<String, Object> outputPayload) {
        jdbcTemplate.update("""
                UPDATE workflow_run
                SET status = 'failed',
                    output_payload = ?::jsonb,
                    error_message = ?,
                    finished_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                """, toJson(outputPayload), errorMessage == null ? "unknown_error" : errorMessage, workflowRunId);
    }

    public void incrementWorkflowStats(Long workflowId) {
        if (workflowId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE workflow_definition
                SET run_count = COALESCE(run_count, 0) + 1,
                    last_run_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                """, workflowId);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "workflow_run_json_serialize_failed");
        }
    }
}
