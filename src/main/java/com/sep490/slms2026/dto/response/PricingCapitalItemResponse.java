package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PricingCapitalItemKind;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingCapitalItemResponse {
    private Long id;
    private String itemName;
    private Integer pricingVersion;
    private PricingCapitalItemKind kind;
    private Long roomId;
    private Boolean houseArea;
    private BigDecimal amount;
    private LocalDate startDate;
    private Integer months;
    private BigDecimal depreciatedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal monthlyAmount;
}
