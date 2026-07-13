package com.proops2026.notificationservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskEventPayload {

    // Schema v2 (IRD-002 amended) — dedup key. May be absent for legacy v1 events (processed without dedup).
    @JsonAlias({"eventId", "event_id"})
    private String eventId;

    @JsonAlias({"eventType", "event_type"})
    private String eventType;

    @JsonAlias({"taskId", "task_id"})
    private String taskId;

    @JsonAlias({"userId", "user_id"})
    private String userId;

    private String timestamp;
}
