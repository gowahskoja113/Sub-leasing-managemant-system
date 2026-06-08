package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PricingScope;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationResultResponse {

    private Long id;
    private Long propertyId;
    private Long inboundContractId;
    private PricingScope pricingScope;
    private Long roomId;
    private String roomNumber;
    private BigDecimal totalRenovationCost;
    private BigDecimal totalEquipmentCost;
    private BigDecimal originalDeposit;
    private BigDecimal totalInvestment;
    private Integer contractMonths;
    private BigDecimal monthlyDepreciation;
    private BigDecimal baseRent;
    private BigDecimal monthlyOperatingCost;
    private BigDecimal suggestedMinPrice;
    private LocalDateTime calculatedAt;
}
