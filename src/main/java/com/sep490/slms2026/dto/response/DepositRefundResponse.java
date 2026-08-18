package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRefundResponse {
    private Long contractId;
    private Long checkoutRequestId;
    private String status;
    private BigDecimal amount;
    private LocalDate refundedAt;
    private String method;
    private String proofUrl;
}
