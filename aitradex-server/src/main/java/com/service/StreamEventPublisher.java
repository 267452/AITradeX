package com.service;

import com.config.AppProperties;
import com.domain.stream.MarketTickStreamEvent;
import com.domain.stream.OrderStatusStreamEvent;
import com.domain.stream.RiskCheckStreamEvent;
import com.domain.stream.WorkflowRunStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class StreamEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(StreamEventPublisher.class);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public StreamEventPublisher(AppProperties properties,
                                ObjectMapper objectMapper,
                                @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishMarketTick(MarketTickStreamEvent event) {
        send(properties.getStreamTopicMarketTick(), event == null ? null : event.symbol(), event);
    }

    public void publishOrderStatus(OrderStatusStreamEvent event) {
        send(properties.getStreamTopicOrderEvent(), event == null ? null : String.valueOf(event.orderId()), event);
    }

    public void publishRiskCheck(RiskCheckStreamEvent event) {
        send(properties.getStreamTopicRiskEvent(), event == null ? null : event.checkName(), event);
    }

    public void publishWorkflowRun(WorkflowRunStreamEvent event) {
        send(properties.getStreamTopicWorkflowEvent(), event == null ? null : event.runId(), event);
    }

    private void send(String topic, String key, Object payload) {
        if (!properties.isStreamEnabled() || kafkaTemplate == null || payload == null) {
            return;
        }
        String safeTopic = topic == null || topic.isBlank() ? null : topic.trim();
        if (safeTopic == null) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(safeTopic, key, body);
        } catch (JsonProcessingException e) {
            logger.warn("Stream event serialize failed: topic={}, reason={}", safeTopic, e.getMessage());
        } catch (Exception e) {
            logger.warn("Stream event publish failed: topic={}, reason={}", safeTopic, e.getMessage());
        }
    }
}
