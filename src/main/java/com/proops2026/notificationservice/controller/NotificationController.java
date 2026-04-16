package com.proops2026.notificationservice.controller;

import com.proops2026.notificationservice.dto.response.MarkReadResponse;
import com.proops2026.notificationservice.dto.response.NotificationResponse;
import com.proops2026.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponse>> listNotifications(
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.ok(notificationService.listNotifications(userId));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<MarkReadResponse> markRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("id") String notificationId
    ) {
        return ResponseEntity.ok(notificationService.markAsRead(userId, notificationId));
    }
}
