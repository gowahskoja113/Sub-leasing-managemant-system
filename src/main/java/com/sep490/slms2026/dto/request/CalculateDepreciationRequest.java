package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalculateDepreciationRequest {

    // Chi phí vận hành hàng tháng (điện chung, internet, bảo trì...)
    @DecimalMin(value = "0.0", message = "Chi phí vận hành không được âm")
    private BigDecimal monthlyOperatingCost;
}
