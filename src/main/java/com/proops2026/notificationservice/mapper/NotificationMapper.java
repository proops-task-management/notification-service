package com.proops2026.notificationservice.mapper;

import com.proops2026.notificationservice.dto.response.NotificationResponse;
import com.proops2026.notificationservice.model.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "isRead", source = "read")
    NotificationResponse toResponse(Notification notification);
}
