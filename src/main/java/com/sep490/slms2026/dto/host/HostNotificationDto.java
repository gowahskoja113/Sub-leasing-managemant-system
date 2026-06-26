package com.sep490.slms2026.dto.host;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record HostNotificationDto(
        String id,
        String type,
        String title,
        String message,
        @JsonProperty("isRead") boolean isRead,
        String priority,
        LocalDateTime createdAt
) {
}
