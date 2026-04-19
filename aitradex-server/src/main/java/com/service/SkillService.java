package com.service;

import com.common.exception.BusinessException;
import com.domain.request.SkillUpsertRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.AdminModuleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {
    private final AdminModuleRepository repository;
    private final SkillFileService skillFileService;
    private final ObjectMapper objectMapper;
    private final boolean localMirrorEnabled;

    public SkillService(AdminModuleRepository repository,
                        SkillFileService skillFileService,
                        ObjectMapper objectMapper,
                        @Value("${app.skills.local-mirror-enabled:false}") boolean localMirrorEnabled) {
        this.repository = repository;
        this.skillFileService = skillFileService;
        this.objectMapper = objectMapper;
        this.localMirrorEnabled = localMirrorEnabled;
    }

    public List<Map<String, Object>> listSkills() {
        List<Map<String, Object>> skills = repository.listSkills();
        List<Map<String, Object>> normalized = new ArrayList<>(skills.size());
        for (Map<String, Object> skill : skills) {
            normalized.add(normalizeSkill(skill));
        }
        return normalized;
    }

    public Map<String, Object> getSkill(long id) {
        Map<String, Object> skill = repository.getSkill(id);
        return skill == null ? null : normalizeSkill(skill);
    }

    @Transactional
    public long createSkill(SkillUpsertRequest request) {
        long skillId = repository.createSkill(request);
        String promptTemplate = defaultString(request.promptTemplate());
        String promptContent = promptTemplate;
        String scriptContent = defaultScriptTemplate(request.name());
        repository.createSkillVersion(
                skillId,
                promptTemplate,
                promptContent,
                scriptContent,
                request.variables(),
                request.tools(),
                request.enabledTools(),
                true,
                buildChecksum(promptTemplate, promptContent, scriptContent, request.variables(), request.tools(), request.enabledTools()));
        repository.ensureSkillRuntimeMetrics(skillId);
        mirrorSkill(skillId, request, promptContent, scriptContent);
        return skillId;
    }

    @Transactional
    public void updateSkill(long id, SkillUpsertRequest request) {
        Map<String, Object> existing = repository.getSkill(id);
        if (existing == null) {
            throw new BusinessException(404, "skill_not_found");
        }

        repository.updateSkill(id, request);
        Map<String, Object> currentVersion = repository.getCurrentSkillVersion(id);

        String promptTemplate = request.promptTemplate() != null
                ? request.promptTemplate()
                : asString(currentVersion.get("prompt_template"));
        String promptContent = asString(currentVersion.get("prompt_content"));
        if (promptContent.isBlank()) {
            promptContent = promptTemplate;
        }
        String scriptContent = asString(currentVersion.get("script_content"));
        String[] variables = request.variables() != null
                ? request.variables()
                : parseJsonStringArray(currentVersion.get("variables_json"));
        String[] tools = request.tools() != null
                ? request.tools()
                : parseJsonStringArray(currentVersion.get("tools_json"));
        String enabledTools = request.enabledTools() != null
                ? request.enabledTools()
                : asString(currentVersion.get("enabled_tools"));

        repository.createSkillVersion(
                id,
                promptTemplate,
                promptContent,
                scriptContent,
                variables,
                tools,
                enabledTools,
                true,
                buildChecksum(promptTemplate, promptContent, scriptContent, variables, tools, enabledTools));
        mirrorSkill(id, request, promptContent, scriptContent);
    }

    @Transactional
    public void deleteSkill(long id) {
        repository.deleteSkill(id);
        skillFileService.deleteSkillFiles(id);
    }

    public String readPrompt(long skillId) {
        Map<String, Object> currentVersion = repository.getCurrentSkillVersion(skillId);
        if (currentVersion == null) {
            throw new BusinessException(404, "skill_not_found");
        }
        String promptContent = asString(currentVersion.get("prompt_content"));
        if (!promptContent.isBlank()) {
            return promptContent;
        }
        String promptTemplate = asString(currentVersion.get("prompt_template"));
        if (!promptTemplate.isBlank()) {
            return promptTemplate;
        }
        return skillFileService.readPrompt(skillId);
    }

    @Transactional
    public void writePrompt(long skillId, String content) {
        Map<String, Object> skill = repository.getSkill(skillId);
        if (skill == null) {
            throw new BusinessException(404, "skill_not_found");
        }
        Map<String, Object> currentVersion = repository.getCurrentSkillVersion(skillId);
        String normalizedContent = normalizeEditorContent(content);
        String promptTemplate = asString(currentVersion.get("prompt_template"));
        if (promptTemplate.isBlank()) {
            promptTemplate = normalizedContent;
        }
        String scriptContent = asString(currentVersion.get("script_content"));
        String[] variables = parseJsonStringArray(currentVersion.get("variables_json"));
        String[] tools = parseJsonStringArray(currentVersion.get("tools_json"));
        String enabledTools = asString(currentVersion.get("enabled_tools"));
        String promptContent = normalizedContent;

        repository.createSkillVersion(
                skillId,
                promptTemplate,
                promptContent,
                scriptContent,
                variables,
                tools,
                enabledTools,
                true,
                buildChecksum(promptTemplate, promptContent, scriptContent, variables, tools, enabledTools));

        if (localMirrorEnabled) {
            skillFileService.writePrompt(skillId, promptContent);
        }
    }

    public String readScript(long skillId) {
        Map<String, Object> currentVersion = repository.getCurrentSkillVersion(skillId);
        if (currentVersion == null) {
            throw new BusinessException(404, "skill_not_found");
        }
        String scriptContent = asString(currentVersion.get("script_content"));
        if (!scriptContent.isBlank()) {
            return scriptContent;
        }
        return skillFileService.readScript(skillId);
    }

    @Transactional
    public void writeScript(long skillId, String content) {
        Map<String, Object> skill = repository.getSkill(skillId);
        if (skill == null) {
            throw new BusinessException(404, "skill_not_found");
        }
        Map<String, Object> currentVersion = repository.getCurrentSkillVersion(skillId);
        String normalizedContent = normalizeEditorContent(content);
        String promptTemplate = asString(currentVersion.get("prompt_template"));
        String promptContent = asString(currentVersion.get("prompt_content"));
        if (promptContent.isBlank()) {
            promptContent = promptTemplate;
        }
        String[] variables = parseJsonStringArray(currentVersion.get("variables_json"));
        String[] tools = parseJsonStringArray(currentVersion.get("tools_json"));
        String enabledTools = asString(currentVersion.get("enabled_tools"));
        String scriptContent = normalizedContent;

        repository.createSkillVersion(
                skillId,
                promptTemplate,
                promptContent,
                scriptContent,
                variables,
                tools,
                enabledTools,
                true,
                buildChecksum(promptTemplate, promptContent, scriptContent, variables, tools, enabledTools));

        if (localMirrorEnabled) {
            skillFileService.writeScript(skillId, scriptContent);
        }
    }

    public Map<String, Object> getSkillDetail(long skillId) {
        Map<String, Object> skill = repository.getSkill(skillId);
        if (skill == null) {
            return null;
        }
        return normalizeSkill(skill);
    }

    private Map<String, Object> normalizeSkill(Map<String, Object> skill) {
        String promptTemplate = asString(skill.get("prompt_template"));
        String promptContent = asString(skill.get("prompt_content"));
        if (promptContent.isBlank()) {
            promptContent = promptTemplate;
        }
        String scriptContent = asString(skill.get("script_content"));
        skill.put("promptTemplate", promptContent);
        skill.put("scriptContent", scriptContent);
        skill.put("enabledTools", asString(skill.get("enabled_tools")));
        return skill;
    }

    private void mirrorSkill(long skillId, SkillUpsertRequest request, String promptContent, String scriptContent) {
        if (!localMirrorEnabled) {
            return;
        }
        skillFileService.updateSkillFiles(
                skillId,
                request.name(),
                request.description(),
                request.icon(),
                request.category(),
                request.status(),
                request.promptTemplate(),
                request.variables(),
                request.tools(),
                request.enabledTools());
        skillFileService.writePrompt(skillId, promptContent);
        skillFileService.writeScript(skillId, scriptContent);
    }

    private String[] parseJsonStringArray(Object value) {
        if (value == null) {
            return new String[0];
        }
        if (value instanceof String[] array) {
            return array;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> item == null ? "" : String.valueOf(item)).toArray(String[]::new);
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return new String[0];
        }
        try {
            return objectMapper.readValue(text, String[].class);
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private String buildChecksum(String promptTemplate,
                                 String promptContent,
                                 String scriptContent,
                                 String[] variables,
                                 String[] tools,
                                 String enabledTools) {
        String payload = defaultString(promptTemplate)
                + "|"
                + defaultString(promptContent)
                + "|"
                + defaultString(scriptContent)
                + "|"
                + String.join(",", variables == null ? new String[0] : variables)
                + "|"
                + String.join(",", tools == null ? new String[0] : tools)
                + "|"
                + defaultString(enabledTools);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                sb.append(String.format("%02x", item));
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String defaultScriptTemplate(String skillName) {
        String safeName = defaultString(skillName).isBlank() ? "new-skill" : skillName;
        return "# Python script for skill: " + safeName + "\n# Add your code here\n";
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeEditorContent(String content) {
        String raw = defaultString(content);
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                return objectMapper.readValue(trimmed, String.class);
            } catch (Exception ignored) {
                // keep raw payload when it is plain text with surrounding quotes
            }
        }
        return raw;
    }
}
