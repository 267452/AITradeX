package com.service;

import com.domain.request.SkillUpsertRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.AdminModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SkillService {
    private final AdminModuleRepository repository;
    private final SkillFileService skillFileService;
    private final ObjectMapper objectMapper;

    public SkillService(AdminModuleRepository repository, SkillFileService skillFileService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.skillFileService = skillFileService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listSkills() {
        List<Map<String, Object>> skills = repository.listSkills();
        for (Map<String, Object> skill : skills) {
            enrichSkillWithFiles(skill);
        }
        return skills;
    }

    public Map<String, Object> getSkill(long id) {
        Map<String, Object> skill = repository.getSkill(id);
        if (skill != null) {
            enrichSkillWithFiles(skill);
        }
        return skill;
    }

    @Transactional
    public void createSkill(SkillUpsertRequest request) {
        repository.createSkill(request);
        long skillId = getLastInsertedSkillId();
        skillFileService.createSkillFiles(
                skillId,
                request.name(),
                request.description(),
                request.icon(),
                request.category(),
                request.status(),
                request.promptTemplate(),
                request.variables(),
                request.tools(),
                request.enabledTools()
        );
    }

    @Transactional
    public void updateSkill(long id, SkillUpsertRequest request) {
        repository.updateSkill(id, request);
        skillFileService.updateSkillFiles(
                id,
                request.name(),
                request.description(),
                request.icon(),
                request.category(),
                request.status(),
                request.promptTemplate(),
                request.variables(),
                request.tools(),
                request.enabledTools()
        );
    }

    @Transactional
    public void deleteSkill(long id) {
        repository.deleteSkill(id);
        skillFileService.deleteSkillFiles(id);
    }

    public String readPrompt(long skillId) {
        return skillFileService.readPrompt(skillId);
    }

    public void writePrompt(long skillId, String content) {
        skillFileService.writePrompt(skillId, content);
    }

    public String readScript(long skillId) {
        return skillFileService.readScript(skillId);
    }

    public void writeScript(long skillId, String content) {
        skillFileService.writeScript(skillId, content);
    }

    private void enrichSkillWithFiles(Map<String, Object> skill) {
        try {
            long skillId = ((Number) skill.get("id")).longValue();
            Map<String, Object> config = skillFileService.readConfig(skillId);
            if (!config.isEmpty()) {
                skill.put("promptTemplate", skillFileService.readPrompt(skillId));
                skill.put("scriptContent", skillFileService.readScript(skillId));
            }
        } catch (Exception e) {
            skill.put("promptTemplate", skill.get("prompt_template"));
            skill.put("scriptContent", "");
        }
    }

    private long getLastInsertedSkillId() {
        List<Map<String, Object>> skills = repository.listSkills();
        if (!skills.isEmpty()) {
            return ((Number) skills.get(0).get("id")).longValue();
        }
        throw new RuntimeException("No skill found after insertion");
    }

    public Map<String, Object> getSkillDetail(long skillId) {
        Map<String, Object> skill = repository.getSkill(skillId);
        if (skill == null) {
            return null;
        }
        enrichSkillWithFiles(skill);
        return skill;
    }
}