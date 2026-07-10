package com.sep490.slms2026.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequestResponse {
    private Long id;
    private Long contractId;
    private String contractCode;
    private String propertyName;
    private String roomNumber;
    private UUID tenantUserId;
    private String tenantFullName;
    private String tenantPhone;
    private LocalDate expectedMoveOutDate;
    private String reason;
    private String note;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String managerNote;
    private String rejectReason;
    private LocalDateTime completedAt;
}
