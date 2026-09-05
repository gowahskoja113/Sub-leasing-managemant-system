package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
    private MaintenanceFlowType flowType;
    private MaintenanceBillingHint billingHint;
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

    private LocalDateTime resolvedAt;
    private String resolutionNote;
    private String repairDescription;

    private String invoiceVendor;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal invoiceAmount;

    private Long previousRequestId;

    private DamageCause damageCause;
    private String faultReason;
    private FaultResolutionPath faultResolutionPath;
    private LocalDate selfRepairDeadline;
    private BigDecimal estimatedDamageAmount;

    private LocalDateTime adminReviewedAt;
    private UUID adminReviewedBy;
    private String adminReviewedByName;
    private Boolean adminApproved;
    private String adminReviewNote;

    private TenantInvoiceResponse issuedInvoice;

    private List<String> beforeImages;
    private List<String> afterImages;
    private List<String> invoiceImages;
    private List<String> faultEvidenceImages;
    private List<String> selfRepairImages;
    /** Gộp tất cả ảnh (tương thích FE cũ). */
    private List<String> images;
    private List<MaintenancePhotoHistoryResponse> photoHistory;

    private LocalDateTime acknowledgedAt;

    private LocalDateTime visitAppointmentAt;
    private LocalDateTime visitArrivalConfirmedAt;
    private LocalDateTime repairAppointmentAt;
    private LocalDateTime repairStartedAt;

    private List<MaintenanceTimelineResponse> timeline;

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
