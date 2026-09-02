package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionRequestResponse {
    private Long id;
    private Long contractId;
    private String contractCode;
    private String propertyName;
    private String roomNumber;
    private UUID tenantUserId;
    private String tenantFullName;
    private String tenantPhone;
    private Integer months;
    private LocalDate newEndDate;
    private String note;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String managerNote;
    private String rejectReason;
}
