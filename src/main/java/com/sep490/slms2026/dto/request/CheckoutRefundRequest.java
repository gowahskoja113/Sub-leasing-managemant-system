package com.sep490.slms2026.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRefundRequest {
    private BigDecimal amount;
    private String method; // BANK_TRANSFER, CASH
    private String proofUrl;
    private LocalDateTime paidAt;
    private String note;
}
