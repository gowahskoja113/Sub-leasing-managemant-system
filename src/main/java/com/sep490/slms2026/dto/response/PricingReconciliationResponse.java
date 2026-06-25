package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.YearMonth;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingReconciliationResponse {

    private Long propertyId;
    private YearMonth month;

    private BigDecimal actualRevenue;
    private BigDecimal occupancyRate;
    private BigDecimal actualProfit;
    private BigDecimal actualCashFlow;

    private BigDecimal fixedOpex;
    private BigDecimal revenueTarget;
    private BigDecimal revenueTargetAtOccupancy;
    private BigDecimal pDesired;

    private Boolean profitTargetMet;
    private Boolean revenueTargetMet;
}
