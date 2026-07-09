package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Flat DTO theo contract section 3 — MaintenanceRequestResponse.
 * Dùng chung cho cả list (Page) và detail.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class MaintenanceRequestResponse {

    private Long id;
    private String title;
    private String description;
    private String category;
    private String priority;
    private MaintenanceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    private String requestCode;
    
    private UUID tenantId;
    private String tenantName;
    private String tenantPhone;

    private Long roomId;
    private String roomName;
    private Long propertyId;
    private String propertyName;
    
    private Long equipmentId;
    private String equipmentName;
    
    private UUID assignedManagerId;
    private String assignedManagerName;
    
    private String scheduledDate;
    private LocalDateTime resolvedAt;
    private BigDecimal repairCost;
    private String resolutionNote;
    
    private CostPaidBy costPaidBy;
    private DamageCause cause;
    private Integer reopenCount;
    
    private List<String> images;

    
    // Main's timeline
    private List<MaintenanceTimelineResponse> timeline;

    // Timeline entry class from feature/maintenance (kept for compatibility)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimelineEntry {
        private MaintenanceStatus oldStatus;
        private MaintenanceStatus newStatus;
        private String note;
        private Long changedBy;
        private String changedByName;
        private LocalDateTime changedAt;
    }
}
