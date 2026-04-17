package com.service;

import com.repository.AdminModuleRepository;
import com.repository.WorkflowRuntimeRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkflowRuntimeService {

    private final WorkflowRuntimeRepository workflowRuntimeRepository;
    private final AdminModuleRepository adminModuleRepository;

    public WorkflowRuntimeService(WorkflowRuntimeRepository workflowRuntimeRepository,
                                  AdminModuleRepository adminModuleRepository) {
        this.workflowRuntimeRepository = workflowRuntimeRepository;
        this.adminModuleRepository = adminModuleRepository;
    }

    public WorkflowRunContext startRun(Long workflowId,
                                       Long conversationId,
                                       String message,
                                       String provider,
                                       String model,
                                       boolean autoExecute) {
        String runId = "run-" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> inputPayload = new LinkedHashMap<>();
        inputPayload.put("message", message);
        inputPayload.put("provider", provider);
        inputPayload.put("model", model);
        inputPayload.put("auto_execute", autoExecute);
        inputPayload.put("conversation_id", conversationId);
        inputPayload.put("workflow_id", workflowId);
        inputPayload.put("started_at", OffsetDateTime.now(ZoneOffset.UTC).toString());

        long workflowRunId = workflowRuntimeRepository.createWorkflowRun(runId, workflowId, conversationId, inputPayload);
        WorkflowRunContext context = new WorkflowRunContext(workflowRunId, runId, workflowId, conversationId);

        workflowRuntimeRepository.appendWorkflowRunStep(
                workflowRunId,
                context.currentStepOrder(),
                "start",
                "开始执行",
                "start",
                "completed",
                Map.of("message", message),
                Map.of("run_id", runId),
                "");

        if (workflowId != null) {
            try {
                Map<String, Object> graph = adminModuleRepository.getWorkflowGraph(workflowId);
                workflowRuntimeRepository.appendWorkflowRunStep(
                        workflowRunId,
                        context.nextStepOrder(),
                        "workflow_graph",
                        "加载工作流拓扑",
                        "graph",
                        "completed",
                        Map.of("workflow_id", workflowId),
                        Map.of(
                                "name", graph.get("name"),
                                "status", graph.get("status"),
                                "node_count", ((java.util.List<?>) graph.getOrDefault("nodes", java.util.List.of())).size(),
                                "edge_count", ((java.util.List<?>) graph.getOrDefault("edges", java.util.List.of())).size()),
                        "");
            } catch (Exception e) {
                workflowRuntimeRepository.appendWorkflowRunStep(
                        workflowRunId,
                        context.nextStepOrder(),
                        "workflow_graph",
                        "加载工作流拓扑",
                        "graph",
                        "failed",
                        Map.of("workflow_id", workflowId),
                        Map.of(),
                        e.getMessage() == null ? "workflow_graph_load_failed" : e.getMessage());
            }
        }

        return context;
    }

    public void recordPlannerRound(WorkflowRunContext context,
                                   int round,
                                   Map<String, Object> plannerPayload) {
        if (context == null) {
            return;
        }
        workflowRuntimeRepository.appendWorkflowRunStep(
                context.workflowRunId(),
                context.nextStepOrder(),
                "planner-round-" + round,
                "模型决策轮次 " + round,
                "llm",
                "completed",
                Map.of("round", round),
                plannerPayload,
                "");
    }

    public void recordToolCall(WorkflowRunContext context,
                               int round,
                               String toolName,
                               Map<String, Object> arguments,
                               Map<String, Object> result) {
        if (context == null) {
            return;
        }
        boolean ok = Boolean.TRUE.equals(result.get("ok"));
        String status = ok ? "completed" : "failed";
        workflowRuntimeRepository.appendWorkflowRunStep(
                context.workflowRunId(),
                context.nextStepOrder(),
                "tool-" + round + "-" + toolName,
                "工具调用 " + toolName,
                "mcp_tool",
                status,
                arguments == null ? Map.of() : arguments,
                result == null ? Map.of() : result,
                ok ? "" : String.valueOf(result.getOrDefault("message", "tool_call_failed")));
    }

    public void completeRun(WorkflowRunContext context, Map<String, Object> outputPayload) {
        if (context == null || !context.markClosed()) {
            return;
        }
        workflowRuntimeRepository.appendWorkflowRunStep(
                context.workflowRunId(),
                context.nextStepOrder(),
                "end",
                "执行完成",
                "end",
                "completed",
                Map.of(),
                outputPayload == null ? Map.of() : outputPayload,
                "");

        workflowRuntimeRepository.completeWorkflowRun(context.workflowRunId(), outputPayload == null ? Map.of() : outputPayload);
        workflowRuntimeRepository.incrementWorkflowStats(context.workflowId());
    }

    public void failRun(WorkflowRunContext context, String errorMessage) {
        if (context == null || !context.markClosed()) {
            return;
        }
        String safeError = errorMessage == null || errorMessage.isBlank() ? "workflow_run_failed" : errorMessage;
        workflowRuntimeRepository.appendWorkflowRunStep(
                context.workflowRunId(),
                context.nextStepOrder(),
                "end",
                "执行失败",
                "end",
                "failed",
                Map.of(),
                Map.of(),
                safeError);
        workflowRuntimeRepository.failWorkflowRun(context.workflowRunId(), safeError, Map.of("error", safeError));
    }

    public static final class WorkflowRunContext {
        private final long workflowRunId;
        private final String runId;
        private final Long workflowId;
        private final Long conversationId;
        private int stepOrder;
        private boolean closed;

        private WorkflowRunContext(long workflowRunId, String runId, Long workflowId, Long conversationId) {
            this.workflowRunId = workflowRunId;
            this.runId = runId;
            this.workflowId = workflowId;
            this.conversationId = conversationId;
            this.stepOrder = 1;
            this.closed = false;
        }

        public long workflowRunId() {
            return workflowRunId;
        }

        public String runId() {
            return runId;
        }

        public Long workflowId() {
            return workflowId;
        }

        public Long conversationId() {
            return conversationId;
        }

        public synchronized int nextStepOrder() {
            stepOrder += 1;
            return stepOrder;
        }

        public synchronized int currentStepOrder() {
            return stepOrder;
        }

        public synchronized boolean markClosed() {
            if (closed) {
                return false;
            }
            closed = true;
            return true;
        }
    }
}
