package com.proops2026.notificationservice.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarkReadResponse {

    private String id;
    private boolean isRead;
}

