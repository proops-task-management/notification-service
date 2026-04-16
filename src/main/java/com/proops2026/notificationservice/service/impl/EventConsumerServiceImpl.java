package com.proops2026.notificationservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proops2026.notificationservice.dto.request.TaskEventPayload;
import com.proops2026.notificationservice.model.Notification;
import com.proops2026.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventConsumerServiceImpl {

    private static final String TASK_EVENTS_QUEUE = "task-events";

    private final StringRedisTemplate redisTemplate;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${notifications.consumer.fixed-delay-ms:1000}")
    @Transactional
    public void pollTaskEvents() {
        while (true) {
            String payload;
            try {
                payload = redisTemplate.opsForList().rightPop(TASK_EVENTS_QUEUE);
            } catch (RuntimeException ex) {
                log.warn("Failed to read task events from Redis", ex);
                return;
            }

            if (payload == null) {
                return;
            }

            consumePayload(payload);
        }
    }

    void consumePayload(String payload) {
        try {
            TaskEventPayload event = objectMapper.readValue(payload, TaskEventPayload.class);
            createNotification(event);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse task event payload: {}", payload, ex);
        }
    }

    private void createNotification(TaskEventPayload event) {
        if (event == null || !StringUtils.hasText(event.getUserId()) || !StringUtils.hasText(event.getEventType())) {
            return;
        }

        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .userId(event.getUserId())
                .eventType(event.getEventType())
                .message(buildMessage(event))
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    private String buildMessage(TaskEventPayload event) {
        String taskId = StringUtils.hasText(event.getTaskId()) ? event.getTaskId() : "unknown";

        return switch (event.getEventType()) {
            case "task.assigned" -> "You have been assigned to task " + taskId + ".";
            case "task.commented" -> "There is a new comment on task " + taskId + ".";
            case "task.status_changed" -> "Task " + taskId + " status was updated.";
            case "task.overdue" -> "Task " + taskId + " is overdue.";
            case "task.created" -> "Task " + taskId + " was created.";
            default -> "Task " + taskId + " has a new update.";
        };
    }
}
