package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class InvoiceUnlockVerifyResponse {
    boolean valid;
    UUID unlockToken;
    LocalDateTime expiresAt;
    String message;
}
