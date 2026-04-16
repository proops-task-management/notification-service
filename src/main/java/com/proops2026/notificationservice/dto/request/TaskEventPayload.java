package com.proops2026.notificationservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskEventPayload {

    @JsonAlias({"eventType", "event_type"})
    private String eventType;

    @JsonAlias({"taskId", "task_id"})
    private String taskId;

    @JsonAlias({"userId", "user_id"})
    private String userId;

    private String timestamp;
}
