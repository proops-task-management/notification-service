package com.proops2026.notificationservice.mapper;

import com.proops2026.notificationservice.dto.response.NotificationResponse;
import com.proops2026.notificationservice.model.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}

