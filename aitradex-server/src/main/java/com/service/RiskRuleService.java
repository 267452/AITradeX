package com.service;

import com.domain.entity.RiskRuleEntity;
import com.domain.request.RiskRuleUpsertRequest;
import com.domain.response.RiskRuleResponse;
import com.repository.RiskRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskRuleService {
    private static final Logger logger = LoggerFactory.getLogger(RiskRuleService.class);
    
    private final RiskRepository riskRepository;
    
    public RiskRuleService(RiskRepository riskRepository) {
        this.riskRepository = riskRepository;
    }
    
    public List<RiskRuleResponse> getRiskRules() {
        logger.info("Getting all risk rules");
        List<RiskRuleEntity> rules = riskRepository.getRiskRules();
        return rules.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    public RiskRuleResponse getRiskRuleById(long id) {
        logger.info("Getting risk rule by id: {}", id);
        RiskRuleEntity rule = riskRepository.getRiskRuleById(id);
        return rule != null ? mapToResponse(rule) : null;
    }
    
    public RiskRuleResponse createRiskRule(RiskRuleUpsertRequest request) {
        logger.info("Creating risk rule: name={}, type={}", request.name(), request.ruleType());
        long id = riskRepository.createRiskRule(
                request.name(),
                request.ruleType(),
                request.ruleConfig(),
                request.enabled(),
                request.priority()
        );
        RiskRuleEntity rule = riskRepository.getRiskRuleById(id);
        return mapToResponse(rule);
    }
    
    public RiskRuleResponse updateRiskRule(long id, RiskRuleUpsertRequest request) {
        logger.info("Updating risk rule: id={}, name={}", id, request.name());
        boolean updated = riskRepository.updateRiskRule(
                id,
                request.name(),
                request.ruleType(),
                request.ruleConfig(),
                request.enabled(),
                request.priority()
        );
        if (!updated) {
            logger.warn("Risk rule not found: id={}", id);
            return null;
        }
        RiskRuleEntity rule = riskRepository.getRiskRuleById(id);
        return mapToResponse(rule);
    }
    
    public boolean deleteRiskRule(long id) {
        logger.info("Deleting risk rule: id={}", id);
        return riskRepository.deleteRiskRule(id);
    }
    
    public boolean toggleRiskRule(long id, boolean enabled) {
        logger.info("Toggling risk rule: id={}, enabled={}", id, enabled);
        return riskRepository.toggleRiskRule(id, enabled);
    }
    
    private RiskRuleResponse mapToResponse(RiskRuleEntity entity) {
        return new RiskRuleResponse(
                entity.id(),
                entity.name(),
                entity.ruleType(),
                entity.ruleConfig(),
                entity.enabled(),
                entity.priority(),
                entity.createdAt(),
                entity.updatedAt()
        );
    }
}