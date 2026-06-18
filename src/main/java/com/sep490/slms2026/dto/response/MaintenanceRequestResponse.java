package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Flat DTO theo contract section 3 — MaintenanceRequestResponse.
 * Dùng chung cho cả list (Page) và detail.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceRequestResponse {

    private Long id;
    private String requestCode;
    private MaintenanceStatus status;
    private MaintenanceCategory category;
    private MaintenancePriority priority;
    private String description;

    // Tenant info
    private Long tenantId;
    private String tenantName;
    private String tenantPhone;

    // Room & Property
    private Long roomId;
    private String roomName;
    private Long propertyId;
    private String propertyName;

    // Equipment (nullable)
    private Long equipmentId;
    private String equipmentName;

    // Assigned Manager (nullable)
    private Long assignedManagerId;
    private String assignedManagerName;

    // Resolution fields (nullable)
    private LocalDateTime scheduledDate;
    private Long repairCost;
    private String resolutionNote;
    private LocalDateTime resolvedAt;

    // Images
    private List<String> images;

    // Audit timeline
    private List<TimelineEntry> timeline;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
