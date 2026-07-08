package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PricingMode;
import com.sep490.slms2026.enums.PricingScope;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationCalculationResponse {

    private Long propertyId;
    private PricingScope pricingScope;
    private PricingMode mode;

    // ===== Tổng hợp cấp tòa (theo PRICING_FORMULA.md) =====
    private BigDecimal cRent;
    private BigDecimal cRenovation;
    private BigDecimal cEquipment;
    private BigDecimal capex;
    private Integer contractMonths;
    private BigDecimal monthlyRecovery;
    private BigDecimal fixedOpex;
    private BigDecimal revenueMin;
    private BigDecimal revenueTarget;
    private BigDecimal pDesired;
    private BigDecimal roiExpected;
    private BigDecimal oOperation;
    private BigDecimal vRate;
    private Double commonAreaM2;
    private Double totalWeight;
    private Integer roomCount;

    private DepreciationResultResponse wholeHouseResult;
    private List<DepreciationResultResponse> roomResults;
}
