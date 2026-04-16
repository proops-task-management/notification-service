package com.proops2026.notificationservice.service.impl;

import com.proops2026.notificationservice.dto.response.NotificationResponse;
import com.proops2026.notificationservice.exception.NotificationNotFoundException;
import com.proops2026.notificationservice.exception.UnauthorizedException;
import com.proops2026.notificationservice.mapper.NotificationMapper;
import com.proops2026.notificationservice.model.Notification;
import com.proops2026.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void listNotifications_returnsMappedResponses() {
        Notification entity = Notification.builder()
                .id("n1")
                .userId("u1")
                .eventType("task.assigned")
                .message("m")
                .isRead(false)
                .createdAt(LocalDateTime.parse("2026-04-15T10:00:00"))
                .build();

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(List.of(entity));
        when(notificationMapper.toResponse(entity)).thenReturn(
                NotificationResponse.builder()
                        .id("n1")
                        .eventType("task.assigned")
                        .message("m")
                        .isRead(false)
                        .createdAt(LocalDateTime.parse("2026-04-15T10:00:00"))
                        .build()
        );

        List<NotificationResponse> responses = notificationService.listNotifications("u1");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getId()).isEqualTo("n1");
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc("u1");
        verify(notificationMapper).toResponse(entity);
    }

    @Test
    void markAsRead_asOwner_setsReadAndSaves() {
        Notification entity = Notification.builder()
                .id("n1")
                .userId("u1")
                .eventType("task.assigned")
                .message("m")
                .isRead(false)
                .build();

        when(notificationRepository.findById("n1")).thenReturn(Optional.of(entity));

        var response = notificationService.markAsRead("u1", "n1");

        assertThat(response.getId()).isEqualTo("n1");
        assertThat(response.isRead()).isTrue();
        assertThat(entity.isRead()).isTrue();
        verify(notificationRepository).save(entity);
    }

    @Test
    void markAsRead_asDifferentUser_returns403() {
        Notification entity = Notification.builder()
                .id("n1")
                .userId("u1")
                .isRead(false)
                .build();

        when(notificationRepository.findById("n1")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> notificationService.markAsRead("u2", "n1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("forbidden");

        verify(notificationRepository, never()).save(entity);
    }

    @Test
    void markAsRead_notFound_returns404() {
        when(notificationRepository.findById("n1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("u1", "n1"))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessage("notification not found");
    }
}
