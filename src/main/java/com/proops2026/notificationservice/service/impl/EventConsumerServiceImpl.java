package com.proops2026.notificationservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proops2026.notificationservice.dto.request.TaskEventPayload;
import com.proops2026.notificationservice.model.Notification;
import com.proops2026.notificationservice.model.ProcessedEvent;
import com.proops2026.notificationservice.repository.NotificationRepository;
import com.proops2026.notificationservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Processes one {@code task-events} payload (IRD-004 amended, ADR-004). The blocking-pop loop
 * lives in {@link TaskEventConsumer}; this bean holds the transactional work so the
 * {@code @Transactional} proxy actually applies (self-invocation from the consumer thread would
 * bypass it).
 *
 * <p>Idempotency: an event's {@code eventId} (schema v2) is recorded in {@code processed_events}
 * FIRST, in the same transaction as the notification — a duplicate {@code eventId} is skipped.
 * Legacy v1 events (no {@code eventId}) are processed without dedup during the transition window.
 * A single consumer thread means the {@code existsById}-then-insert check is race-free; the PK
 * still backstops it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventConsumerServiceImpl {

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(String payload) {
        TaskEventPayload event = parse(payload);
        if (event == null || !StringUtils.hasText(event.getUserId()) || !StringUtils.hasText(event.getEventType())) {
            return;
        }

        String eventId = event.getEventId();
        if (StringUtils.hasText(eventId)) {
            if (processedEventRepository.existsById(eventId)) {
                log.debug("Duplicate event {} already processed — skipping", eventId);
                return;
            }
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .processedAt(LocalDateTime.now())
                    .build());
        }

        notificationRepository.save(buildNotification(event));
        log.info("Notification stored for user {} (event {}, id {})",
                event.getUserId(), event.getEventType(), eventId);
    }

    private TaskEventPayload parse(String payload) {
        try {
            return objectMapper.readValue(payload, TaskEventPayload.class);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse task event payload: {}", payload, ex);
            return null;
        }
    }

    private Notification buildNotification(TaskEventPayload event) {
        return Notification.builder()
                .id(UUID.randomUUID().toString())
                .userId(event.getUserId())
                .eventType(event.getEventType())
                .message(buildMessage(event))
                .isRead(false)
                .build();
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
