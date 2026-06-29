package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrEvnBillResponse {
    private BigDecimal totalKwh;
    private BigDecimal totalAmount;
    private String billingPeriod;
    private String rawText;
}
