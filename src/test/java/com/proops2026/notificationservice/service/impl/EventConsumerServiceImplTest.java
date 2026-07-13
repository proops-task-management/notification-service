package com.proops2026.notificationservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.proops2026.notificationservice.model.Notification;
import com.proops2026.notificationservice.model.ProcessedEvent;
import com.proops2026.notificationservice.repository.NotificationRepository;
import com.proops2026.notificationservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private EventConsumerServiceImpl eventProcessor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        eventProcessor = new EventConsumerServiceImpl(notificationRepository, processedEventRepository, objectMapper);
    }

    @Test
    void process_taskAssigned_recordsEventAndCreatesNotification() {
        when(processedEventRepository.existsById("evt-1")).thenReturn(false);

        eventProcessor.process("""
                {"eventId":"evt-1","eventType":"task.assigned","taskId":"task-1","userId":"user-1","timestamp":"2026-07-13T09:00:00Z"}
                """);

        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getEventType()).isEqualTo("task.assigned");
        assertThat(saved.getMessage()).isEqualTo("You have been assigned to task task-1.");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void process_taskCommented_buildsCommentMessage() {
        when(processedEventRepository.existsById("evt-2")).thenReturn(false);

        eventProcessor.process("""
                {"eventId":"evt-2","eventType":"task.commented","taskId":"task-2","userId":"user-9","timestamp":"2026-07-13T09:01:00Z"}
                """);

        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getMessage()).isEqualTo("There is a new comment on task task-2.");
    }

    @Test
    void process_duplicateEventId_isSkipped() {
        when(processedEventRepository.existsById("evt-dup")).thenReturn(true);

        eventProcessor.process("""
                {"eventId":"evt-dup","eventType":"task.assigned","taskId":"task-1","userId":"user-1","timestamp":"2026-07-13T09:00:00Z"}
                """);

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void process_legacyV1NoEventId_createsNotificationWithoutDedup() {
        eventProcessor.process("""
                {"eventType":"task.created","taskId":"task-7","userId":"user-3","timestamp":"2026-07-13T09:02:00Z"}
                """);

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getMessage()).isEqualTo("Task task-7 was created.");
    }

    @Test
    void process_blankUserId_ignoresEvent() {
        eventProcessor.process("""
                {"eventId":"evt-3","eventType":"task.assigned","taskId":"task-1","userId":"","timestamp":"2026-07-13T09:00:00Z"}
                """);

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
