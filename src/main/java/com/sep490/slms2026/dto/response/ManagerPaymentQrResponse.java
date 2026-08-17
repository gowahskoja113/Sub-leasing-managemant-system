package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class ManagerPaymentQrResponse {
    BigDecimal amount;
    String qrCode;
    String checkoutUrl;
    Long orderCode;
    LocalDateTime expiresAt;
}
