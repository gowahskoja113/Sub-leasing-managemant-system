package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Response DTO cho GET /api/v1/equipment/{id}/maintenance-history
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentMaintenanceHistoryResponse {
    private Long id;
    private Long equipmentId;
    private Long maintenanceRequestId;
    private String requestCode;
    private LocalDateTime maintenanceDate;
    private Long repairCost;
    private String note;
}
