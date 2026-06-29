package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerPaymentResponse {
    private Long id;
    private String invoiceCode;
    private String tenantName;
    private String roomNumber;
    private String propertyName;
    private BigDecimal amount;
    private String method;
    private String status;
    private String transferContent;
    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;
}
