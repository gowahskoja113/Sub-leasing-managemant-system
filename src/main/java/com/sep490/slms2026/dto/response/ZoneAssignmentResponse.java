package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ZoneAssignmentResponse {
    private UUID zoneId;
    private String zoneName;
    private UUID managerId;
    private String managerUsername;
    private String managerFullName;
    private String managerPhone;
    private LocalDateTime assignedAt;
    private UUID assignedBy;
    private String assignedByUsername;
    private Integer activeProperties;
}
