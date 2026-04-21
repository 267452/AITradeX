package com.controller.monitor;

import com.common.api.ApiResponse;
import com.common.exception.BusinessException;
import com.config.AppProperties;
import com.domain.response.FlinkComputeMetricsResponse;
import com.domain.response.MonitorSummaryResponse;
import com.domain.response.OrderPageResponse;
import com.repository.MonitorRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {
    private final MonitorRepository monitorRepository;
    private final AppProperties properties;

    public MonitorController(MonitorRepository monitorRepository, AppProperties properties) {
        this.monitorRepository = monitorRepository;
        this.properties = properties;
    }

    @GetMapping("/summary")
    public ApiResponse<MonitorSummaryResponse> monitorSummary() {
        return ApiResponse.success(monitorRepository.getMonitorSummary());
    }

    @GetMapping("/orders")
    public ApiResponse<OrderPageResponse> monitorOrders(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(monitorRepository.listOrdersPaginated(page, pageSize));
    }

    @GetMapping("/flink/metrics")
    public ApiResponse<FlinkComputeMetricsResponse> flinkMetrics() {
        return ApiResponse.success(monitorRepository.getFlinkComputeMetrics(
                properties.isFlinkComputeEnabled(),
                properties.getFlinkComputeEngine(),
                properties.getFlinkJobName()));
    }

    @GetMapping("/flink/decision-signals")
    public ApiResponse<List<Map<String, Object>>> flinkDecisionSignals(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) Boolean riskGatePassed) {
        return ApiResponse.success(monitorRepository.listFlinkDecisionSignals(limit, symbol, side, riskGatePassed));
    }

    @GetMapping("/risk/rules")
    public ApiResponse<Map<String, Object>> riskRules() {
        return ApiResponse.success(Map.of(
                "max_qty", properties.getRiskMaxQty(),
                "max_notional", properties.getRiskMaxNotional(),
                "allow_short", properties.isRiskAllowShort()));
    }

    @GetMapping("/workflow-runs")
    public ApiResponse<List<Map<String, Object>>> workflowRuns(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) Long workflowRunId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startedTo) {
        return ApiResponse.success(monitorRepository.listWorkflowRuns(
                limit,
                runId,
                workflowRunId,
                status,
                startedFrom,
                startedTo));
    }

    @GetMapping("/workflow-runs/{runId}")
    public ApiResponse<Map<String, Object>> workflowRunDetail(@PathVariable String runId) {
        Map<String, Object> detail = monitorRepository.getWorkflowRunDetail(runId);
        if (detail == null) {
            throw new BusinessException(404, "workflow_run_not_found");
        }
        return ApiResponse.success(detail);
    }

    @GetMapping("/workflow-quality")
    public ApiResponse<Map<String, Object>> workflowQuality(
            @RequestParam(defaultValue = "24") int windowHours,
            @RequestParam(defaultValue = "60") int bucketMinutes,
            @RequestParam(defaultValue = "72") int limit) {
        return ApiResponse.success(monitorRepository.getWorkflowQualityMetrics(windowHours, bucketMinutes, limit));
    }
}
