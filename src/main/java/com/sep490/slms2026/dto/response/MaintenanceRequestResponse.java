package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    
    private java.util.UUID tenantId;
    private String tenantName;
    private String tenantPhone;
    
    private Long roomId;
    private String roomName;
    private Long propertyId;
    private String propertyName;
    
    private Long equipmentId;
    private String equipmentName;
    
    private java.util.UUID assignedManagerId;
    private String assignedManagerName;
    
    private String scheduledDate;
    private LocalDateTime resolvedAt;
    private java.math.BigDecimal repairCost;
    private String resolutionNote;
    
    private java.util.List<String> images;
    private java.util.List<MaintenanceTimelineResponse> timeline;
}
