package com.proops2026.notificationservice.service;

import com.proops2026.notificationservice.dto.response.MarkReadResponse;
import com.proops2026.notificationservice.dto.response.NotificationResponse;

import java.util.List;

public interface NotificationService {

    List<NotificationResponse> listNotifications(String userId);

    MarkReadResponse markAsRead(String userId, String notificationId);
}
