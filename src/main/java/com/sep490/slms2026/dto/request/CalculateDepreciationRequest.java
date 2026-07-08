package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.PricingMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculateDepreciationRequest {

    /** FORWARD = biết pDesired; REVERSE = biết roiExpected. */
    private PricingMode mode;

    /** Lợi nhuận ròng mong muốn mỗi tháng (VND). Bắt buộc khi mode = FORWARD. */
    @Digits(integer = 15, fraction = 0)
    @DecimalMin(value = "0")
    private BigDecimal pDesired;

    /** ROI mong muốn mỗi năm (%). Bắt buộc khi mode = REVERSE. */
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal roiExpected;

    /** Chi phí vận hành cố định mỗi tháng. Mặc định 0. */
    @Digits(integer = 15, fraction = 0)
    @DecimalMin(value = "0")
    @Builder.Default
    private BigDecimal oOperation = BigDecimal.ZERO;

    /** Tỷ lệ dự phòng trống phòng (0.10 = 10%). Mặc định 0.10. */
    @DecimalMin(value = "0")
    private BigDecimal vRate;

    /** Hệ số chất lượng theo roomId. Mặc định 1.0 cho mọi phòng. */
    private Map<Long, BigDecimal> roomQualityFactors;
}
