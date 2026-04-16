package com.proops2026.notificationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proops2026.notificationservice.config.SecurityConfig;
import com.proops2026.notificationservice.dto.response.MarkReadResponse;
import com.proops2026.notificationservice.dto.response.NotificationResponse;
import com.proops2026.notificationservice.exception.UnauthorizedException;
import com.proops2026.notificationservice.service.NotificationService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @Test
    void listNotifications_missingHeaders_returns401() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("unauthorized"));
    }

    @Test
    void listNotifications_returnsCallerNotificationsOnly() throws Exception {
        List<NotificationResponse> response = List.of(
                NotificationResponse.builder()
                        .id("n1")
                        .eventType("task.assigned")
                        .message("You have been assigned a new task")
                        .isRead(false)
                        .createdAt(LocalDateTime.parse("2026-04-15T10:00:00"))
                        .build()
        );
        when(notificationService.listNotifications("u1")).thenReturn(response);

        mockMvc.perform(get("/notifications")
                        .header("X-User-Id", "u1")
                        .header("X-User-Role", "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("n1"))
                .andExpect(jsonPath("$[0].event_type").value("task.assigned"))
                .andExpect(jsonPath("$[0].message").value("You have been assigned a new task"))
                .andExpect(jsonPath("$[0].is_read").value(false))
                .andExpect(jsonPath("$[0].created_at").value("2026-04-15T10:00:00"));
    }

    @Test
    void listNotifications_whenUserHasNoNotifications_returnsEmptyList() throws Exception {
        when(notificationService.listNotifications("u1")).thenReturn(List.of());

        mockMvc.perform(get("/notifications")
                        .header("X-User-Id", "u1")
                        .header("X-User-Role", "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(0)));
    }

    @Test
    void markAsRead_asOwner_returns200() throws Exception {
        when(notificationService.markAsRead("u1", "n1"))
                .thenReturn(MarkReadResponse.builder().id("n1").isRead(true).build());

        mockMvc.perform(patch("/notifications/n1/read")
                        .header("X-User-Id", "u1")
                        .header("X-User-Role", "member")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("n1"))
                .andExpect(jsonPath("$.is_read").value(true));
    }

    @Test
    void markAsRead_asDifferentUser_returns403() throws Exception {
        when(notificationService.markAsRead("u2", "n1")).thenThrow(new UnauthorizedException("forbidden"));

        mockMvc.perform(patch("/notifications/n1/read")
                        .header("X-User-Id", "u2")
                        .header("X-User-Role", "member"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("forbidden"));
    }
}

