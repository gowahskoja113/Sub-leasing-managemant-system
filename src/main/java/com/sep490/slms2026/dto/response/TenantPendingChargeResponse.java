package com.sep490.slms2026.dto.response;

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
public class TenantPendingChargeResponse {
    private Long id;
    private Long tenantContractId;
    private Long invoiceId;
    private BigDecimal amount;
    private String category;
    private String note;
    private String status;
    private LocalDateTime createdAt;
}
