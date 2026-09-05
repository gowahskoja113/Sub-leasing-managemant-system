package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerPaymentHistoryResponse {
    private Long id;
    private Long invoiceId;
    private String invoiceCode;
    private String invoiceType;
    private Long contractId;
    private String tenantName;
    private String propertyName;
    private String roomNumber;
    private BigDecimal amount;
    private String method;
    private LocalDateTime paidAt;
    private String transactionId;
}
