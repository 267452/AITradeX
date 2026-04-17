package com.controller.monitor;

import com.common.api.ApiResponse;
import com.config.AppProperties;
import com.domain.response.FlinkComputeMetricsResponse;
import com.domain.response.MonitorSummaryResponse;
import com.domain.response.OrderPageResponse;
import com.repository.MonitorRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/risk/rules")
    public ApiResponse<Map<String, Object>> riskRules() {
        return ApiResponse.success(Map.of(
                "max_qty", properties.getRiskMaxQty(),
                "max_notional", properties.getRiskMaxNotional(),
                "allow_short", properties.isRiskAllowShort()));
    }
}
