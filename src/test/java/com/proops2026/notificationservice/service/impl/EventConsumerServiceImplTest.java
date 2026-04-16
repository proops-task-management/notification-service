package com.proops2026.notificationservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.proops2026.notificationservice.model.Notification;
import com.proops2026.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private NotificationRepository notificationRepository;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private EventConsumerServiceImpl eventConsumerService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        eventConsumerService = new EventConsumerServiceImpl(redisTemplate, notificationRepository, objectMapper);
    }

    @Test
    void pollTaskEvents_taskAssigned_createsNotification() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPop("task-events"))
                .thenReturn("""
                        {"eventType":"task.assigned","taskId":"task-1","userId":"user-1","timestamp":"2026-04-17T09:00:00Z"}
                        """)
                .thenReturn(null);

        eventConsumerService.pollTaskEvents();

        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getEventType()).isEqualTo("task.assigned");
        assertThat(saved.getMessage()).isEqualTo("You have been assigned to task task-1.");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void pollTaskEvents_taskCommented_createsNotification() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPop("task-events"))
                .thenReturn("""
                        {"eventType":"task.commented","taskId":"task-2","userId":"user-9","timestamp":"2026-04-17T09:01:00Z"}
                        """)
                .thenReturn(null);

        eventConsumerService.pollTaskEvents();

        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getMessage()).isEqualTo("There is a new comment on task task-2.");
    }

    @Test
    void pollTaskEvents_blankUserId_ignoresEvent() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPop("task-events"))
                .thenReturn("""
                        {"eventType":"task.assigned","taskId":"task-1","userId":"","timestamp":"2026-04-17T09:00:00Z"}
                        """)
                .thenReturn(null);

        eventConsumerService.pollTaskEvents();

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }
}
