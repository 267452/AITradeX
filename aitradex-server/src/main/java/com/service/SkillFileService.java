package com.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class SkillFileService {
    private final Path skillsBasePath;
    private final ObjectMapper objectMapper;

    public SkillFileService(
            @Value("${app.skills.path:data/skills}") String skillsPath,
            ObjectMapper objectMapper) {
        this.skillsBasePath = Paths.get(skillsPath).toAbsolutePath();
        this.objectMapper = objectMapper;
        initDirectory();
    }

    private void initDirectory() {
        try {
            Files.createDirectories(skillsBasePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create skills directory: " + skillsBasePath, e);
        }
    }

    public Path getSkillPath(long skillId) {
        return skillsBasePath.resolve(String.valueOf(skillId));
    }

    public void createSkillFiles(long skillId, String name, String description, String icon,
                                  String category, String status, String promptTemplate,
                                  Object variables, Object tools, String enabledTools) {
        try {
            Path skillPath = getSkillPath(skillId);
            Files.createDirectories(skillPath);

            Map<String, Object> config = Map.of(
                    "name", name,
                    "description", description != null ? description : "",
                    "icon", icon != null ? icon : "⚡",
                    "category", category != null ? category : "general",
                    "status", status != null ? status : "enabled",
                    "variables", variables != null ? variables : "[]",
                    "tools", tools != null ? tools : "[]",
                    "enabled_tools", enabledTools != null ? enabledTools : ""
            );

            Files.writeString(skillPath.resolve("config.json"), objectMapper.writeValueAsString(config));

            Files.writeString(skillPath.resolve("prompt.md"),
                    promptTemplate != null ? promptTemplate : "");

            Files.writeString(skillPath.resolve("script.py"),
                    "# Python script for skill: " + name + "\n# Add your code here\n");

        } catch (IOException e) {
            throw new RuntimeException("Failed to create skill files for skill: " + skillId, e);
        }
    }

    public void updateSkillFiles(long skillId, String name, String description, String icon,
                                  String category, String status, String promptTemplate,
                                  Object variables, Object tools, String enabledTools) {
        try {
            Path skillPath = getSkillPath(skillId);
            if (!Files.exists(skillPath)) {
                createSkillFiles(skillId, name, description, icon, category, status,
                        promptTemplate, variables, tools, enabledTools);
                return;
            }

            Map<String, Object> config = Map.of(
                    "name", name,
                    "description", description != null ? description : "",
                    "icon", icon != null ? icon : "⚡",
                    "category", category != null ? category : "general",
                    "status", status != null ? status : "enabled",
                    "variables", variables != null ? variables : "[]",
                    "tools", tools != null ? tools : "[]",
                    "enabled_tools", enabledTools != null ? enabledTools : ""
            );

            Files.writeString(skillPath.resolve("config.json"), objectMapper.writeValueAsString(config));

            if (promptTemplate != null) {
                Files.writeString(skillPath.resolve("prompt.md"), promptTemplate);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to update skill files for skill: " + skillId, e);
        }
    }

    public void deleteSkillFiles(long skillId) {
        try {
            Path skillPath = getSkillPath(skillId);
            if (Files.exists(skillPath)) {
                Files.walk(skillPath)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete skill files for skill: " + skillId, e);
        }
    }

    public String readPrompt(long skillId) {
        try {
            Path promptPath = getSkillPath(skillId).resolve("prompt.md");
            if (Files.exists(promptPath)) {
                return Files.readString(promptPath);
            }
            return "";
        } catch (IOException e) {
            throw new RuntimeException("Failed to read prompt for skill: " + skillId, e);
        }
    }

    public void writePrompt(long skillId, String content) {
        try {
            Path skillPath = getSkillPath(skillId);
            Files.createDirectories(skillPath);
            Files.writeString(skillPath.resolve("prompt.md"), content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write prompt for skill: " + skillId, e);
        }
    }

    public String readScript(long skillId) {
        try {
            Path scriptPath = getSkillPath(skillId).resolve("script.py");
            if (Files.exists(scriptPath)) {
                return Files.readString(scriptPath);
            }
            return "";
        } catch (IOException e) {
            throw new RuntimeException("Failed to read script for skill: " + skillId, e);
        }
    }

    public void writeScript(long skillId, String content) {
        try {
            Path skillPath = getSkillPath(skillId);
            Files.createDirectories(skillPath);
            Files.writeString(skillPath.resolve("script.py"), content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write script for skill: " + skillId, e);
        }
    }

    public Map<String, Object> readConfig(long skillId) {
        try {
            Path configPath = getSkillPath(skillId).resolve("config.json");
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                return objectMapper.readValue(content, Map.class);
            }
            return Map.of();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config for skill: " + skillId, e);
        }
    }
}