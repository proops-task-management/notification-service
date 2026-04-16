package com.proops2026.notificationservice.service.impl;

import com.proops2026.notificationservice.dto.response.MarkReadResponse;
import com.proops2026.notificationservice.dto.response.NotificationResponse;
import com.proops2026.notificationservice.exception.NotificationNotFoundException;
import com.proops2026.notificationservice.exception.UnauthorizedException;
import com.proops2026.notificationservice.mapper.NotificationMapper;
import com.proops2026.notificationservice.model.Notification;
import com.proops2026.notificationservice.repository.NotificationRepository;
import com.proops2026.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public MarkReadResponse markAsRead(String userId, String notificationId) {
        Notification notification = findNotificationOrThrow(notificationId);
        requireOwner(notification, userId);
        notification.setRead(true);
        notificationRepository.save(notification);
        return MarkReadResponse.builder()
                .id(notification.getId())
                .isRead(true)
                .build();
    }

    private Notification findNotificationOrThrow(String notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(NotificationNotFoundException::new);
    }

    private void requireOwner(Notification notification, String userId) {
        if (!userId.equals(notification.getUserId())) {
            throw new UnauthorizedException("forbidden");
        }
    }
}
