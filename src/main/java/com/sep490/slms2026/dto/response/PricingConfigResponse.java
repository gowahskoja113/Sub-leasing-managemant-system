package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PricingMode;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingConfigResponse {

    private PricingMode mode;
    private BigDecimal pDesired;
    private BigDecimal roiExpected;
    private BigDecimal oOperation;
    private Map<UUID, BigDecimal> managerSalaries;
    private BigDecimal annualIncreasePct;
    private int escalationGraceMonths;
    private int newYearPriceLeadMonths;
    private BigDecimal vRatePct;
    private int handoverBufferMonths;
    private LocalDateTime updatedAt;
}
