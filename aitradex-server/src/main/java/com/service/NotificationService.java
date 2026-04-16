package com.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.AdminModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AdminModuleRepository adminModuleRepository;

    public record NotificationMessage(String title, String content, String channelType) {}

    public boolean sendNotification(String title, String content) {
        List<Map<String, Object>> channels = adminModuleRepository.listNotificationChannels();

        boolean anySuccess = false;
        for (Map<String, Object> channel : channels) {
            Boolean enabled = (Boolean) channel.get("enabled");
            if (enabled == null || !enabled) {
                continue;
            }

            String channelType = (String) channel.get("channel_type");
            Map<String, Object> config = (Map<String, Object>) channel.get("config");

            try {
                boolean success = switch (channelType) {
                    case "feishu" -> sendFeishuNotification(config, title, content);
                    case "wecom" -> sendWecomNotification(config, title, content);
                    default -> {
                        log.warn("Unknown channel type: {}", channelType);
                        yield false;
                    }
                };
                if (success) anySuccess = true;
            } catch (Exception e) {
                log.error("Failed to send notification via {}: {}", channelType, e.getMessage());
            }
        }
        return anySuccess;
    }

    private boolean sendFeishuNotification(Map<String, Object> config, String title, String content) {
        String webhookUrl = (String) config.get("webhook_url");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Feishu webhook URL is empty");
            return false;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "text");

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", title + "\n\n" + content);
        body.put("text", textContent);

        try {
            String response = WebClient.create()
                    .post()
                    .uri(webhookUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Feishu notification sent: {}", response);
            return true;
        } catch (Exception e) {
            log.error("Failed to send Feishu notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendWecomNotification(Map<String, Object> config, String title, String content) {
        String webhookUrl = (String) config.get("webhook_url");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("WeCom webhook URL is empty");
            return false;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "text");

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", title + "\n\n" + content);
        body.put("text", textContent);

        try {
            String response = WebClient.create()
                    .post()
                    .uri(webhookUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("WeCom notification sent: {}", response);
            return true;
        } catch (Exception e) {
            log.error("Failed to send WeCom notification: {}", e.getMessage());
            return false;
        }
    }
}
