package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPaymentResponse {
    private Long id;
    private Long invoiceId;
    private String invoiceCode;
    private String invoiceType;
    private BigDecimal amount;
    private String method;
    private LocalDateTime paidAt;
    private String transactionId;
    private String propertyName;
    private String roomNumber;
}
