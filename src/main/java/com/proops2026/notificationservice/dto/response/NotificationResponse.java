package com.proops2026.notificationservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {

    private String id;
    private String eventType;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}

