package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.PricingMode;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePricingConfigRequest {

    @NotNull
    private PricingMode mode;

    @Digits(integer = 15, fraction = 0)
    @DecimalMin("0")
    private BigDecimal pDesired;

    @DecimalMin("0")
    private BigDecimal roiExpected;

    @NotNull
    @Digits(integer = 15, fraction = 0)
    @DecimalMin("0")
    private BigDecimal oOperation;

    /** Lương từng quản lý (userId → VND). Bỏ qua / không lưu mức ≤ 0. */
    private Map<UUID, BigDecimal> managerSalaries;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal annualIncreasePct;

    @NotNull
    @Min(0)
    @Max(24)
    private Integer escalationGraceMonths;

    @NotNull
    @Min(0)
    @Max(11)
    private Integer newYearPriceLeadMonths;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal vRatePct;

    @NotNull
    @Min(0)
    @Max(12)
    private Integer handoverBufferMonths;
}
