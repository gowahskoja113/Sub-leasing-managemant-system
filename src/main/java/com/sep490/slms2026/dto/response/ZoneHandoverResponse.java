package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ZoneHandoverResponse {
    private Long id;
    private UUID zoneId;
    private UUID fromManagerId;
    private String fromManagerUsername;
    private UUID toManagerId;
    private String toManagerUsername;
    private UUID changedBy;
    private String changedByUsername;
    private LocalDateTime changedAt;
    private Integer affectedProperties;
    private Integer affectedContracts;
}
