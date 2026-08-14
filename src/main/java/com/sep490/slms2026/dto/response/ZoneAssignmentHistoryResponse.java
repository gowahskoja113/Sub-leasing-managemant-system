package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ZoneAssignmentHistoryResponse {
    private UUID zoneId;
    private String zoneName;
    private String action; // ASSIGNED or REVOKED
    private LocalDateTime at;
    private UUID byUserId;
    private String byUsername;
    private Integer properties;
}
