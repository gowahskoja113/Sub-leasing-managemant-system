package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MaintenanceTimelineResponse {
    private String oldStatus;
    private String newStatus;
    private String note;
    private String changedBy;
    private String changedByName;
    private LocalDateTime changedAt;
}
