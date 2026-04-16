package com.controller.admin;

import com.common.api.ApiResponse;
import com.domain.request.RiskRuleUpsertRequest;
import com.domain.response.RiskRuleResponse;
import com.service.RiskRuleService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/risk")
public class RiskController {
    private static final Logger logger = LoggerFactory.getLogger(RiskController.class);
    
    private final RiskRuleService riskRuleService;
    
    public RiskController(RiskRuleService riskRuleService) {
        this.riskRuleService = riskRuleService;
    }
    
    @GetMapping("/rules")
    public ApiResponse<List<RiskRuleResponse>> getRiskRules() {
        logger.info("API: Getting all risk rules");
        List<RiskRuleResponse> rules = riskRuleService.getRiskRules();
        return ApiResponse.success(rules);
    }
    
    @GetMapping("/rules/{id}")
    public ApiResponse<RiskRuleResponse> getRiskRuleById(@PathVariable long id) {
        logger.info("API: Getting risk rule by id: {}", id);
        RiskRuleResponse rule = riskRuleService.getRiskRuleById(id);
        return rule != null ? ApiResponse.success(rule) : ApiResponse.error("risk_rule_not_found");
    }
    
    @PostMapping("/rules")
    public ApiResponse<RiskRuleResponse> createRiskRule(@RequestBody RiskRuleUpsertRequest request) {
        logger.info("API: Creating risk rule");
        RiskRuleResponse rule = riskRuleService.createRiskRule(request);
        return ApiResponse.success(rule);
    }
    
    @PutMapping("/rules/{id}")
    public ApiResponse<RiskRuleResponse> updateRiskRule(@PathVariable long id, @RequestBody RiskRuleUpsertRequest request) {
        logger.info("API: Updating risk rule: id={}", id);
        RiskRuleResponse rule = riskRuleService.updateRiskRule(id, request);
        return rule != null ? ApiResponse.success(rule) : ApiResponse.error("risk_rule_not_found");
    }
    
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRiskRule(@PathVariable long id) {
        logger.info("API: Deleting risk rule: id={}", id);
        boolean deleted = riskRuleService.deleteRiskRule(id);
        return deleted ? ApiResponse.success() : ApiResponse.error("risk_rule_not_found");
    }
    
    @PutMapping("/rules/{id}/toggle")
    public ApiResponse<Void> toggleRiskRule(@PathVariable long id, @RequestParam boolean enabled) {
        logger.info("API: Toggling risk rule: id={}, enabled={}", id, enabled);
        boolean toggled = riskRuleService.toggleRiskRule(id, enabled);
        return toggled ? ApiResponse.success() : ApiResponse.error("risk_rule_not_found");
    }
}