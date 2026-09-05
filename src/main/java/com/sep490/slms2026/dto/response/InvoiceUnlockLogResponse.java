package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.InvoiceUnlockPurpose;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class InvoiceUnlockLogResponse {
    Long id;
    UUID managerId;
    String managerName;
    Long invoiceId;
    String invoiceCode;
    InvoiceUnlockPurpose purpose;
    UUID unlockedByAdmin;
    String adminName;
    Long passcodeId;
    boolean success;
    String paymentResult;
    LocalDateTime createdAt;
}
