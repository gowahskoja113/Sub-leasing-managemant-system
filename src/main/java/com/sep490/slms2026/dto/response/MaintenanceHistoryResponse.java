package com.sep490.slms2026.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class MaintenanceHistoryResponse {
    private UUID id;
    private UUID performedById;
    private String performedByName;
    private String statusChangedTo;
    private String actionTaken;
    private BigDecimal cost;
    private LocalDateTime performedAt;
}