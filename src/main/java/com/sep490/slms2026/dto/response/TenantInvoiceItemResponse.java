package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantInvoiceItemResponse {
    private String label;
    private BigDecimal amount;
}
