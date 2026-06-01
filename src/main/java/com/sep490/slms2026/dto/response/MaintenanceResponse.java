package com.sep490.slms2026.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class MaintenanceResponse {
    private UUID id;
    private UUID equipmentId;
    private String equipmentName;
    private UUID tenantId;
    private String tenantName;
    private UUID contractId;
    private String category;
    private String description;
    private String priority;
    private String status;
    private BigDecimal repairCost;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private List<MaintenancePhotoResponse> photos;
    private List<MaintenanceHistoryResponse> histories;
}