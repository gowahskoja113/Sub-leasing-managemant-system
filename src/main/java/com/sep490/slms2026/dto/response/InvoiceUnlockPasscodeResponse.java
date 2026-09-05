package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.InvoiceUnlockPurpose;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class InvoiceUnlockPasscodeResponse {
    Long id;
    String passcode;
    Long invoiceId;
    InvoiceUnlockPurpose purpose;
    UUID createdBy;
    String note;
    LocalDateTime expiresAt;
    LocalDateTime usedAt;
    UUID usedBy;
    LocalDateTime createdAt;
    boolean usable;
    String message;
}
