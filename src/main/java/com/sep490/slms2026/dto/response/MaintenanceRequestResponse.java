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
    
    private LocalDateTime acknowledgedAt;
    private String scheduledSlots;
    private String confirmedSlot;
    private String onHoldReason;
    private ApprovalStatus approvalStatus;
    private LocalDateTime doneAt;
    private LocalDateTime tenantConfirmedAt;
    private Integer reopenCount;
    private String technicianId;
    private CostPaidBy costPaidBy;
    private DamageCause cause;
    private BigDecimal repairCost;
    private String resolutionNote;
    private String beforeImageUrls;
    private String afterImageUrls;
}
