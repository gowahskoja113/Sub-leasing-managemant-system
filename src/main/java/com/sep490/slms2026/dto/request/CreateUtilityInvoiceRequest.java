package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUtilityInvoiceRequest {

    @NotBlank(message = "Loại tiện ích không được để trống")
    private String type;

    @NotBlank(message = "Kỳ thanh toán không được để trống")
    private String billingPeriod;

    @NotNull(message = "Chỉ số kỳ trước không được để trống")
    private BigDecimal prevReading;

    @NotNull(message = "Chỉ số mới không được để trống")
    private BigDecimal newReading;

    @NotNull(message = "Tiêu thụ không được để trống")
    private BigDecimal consumption;

    @NotNull(message = "Đơn giá không được để trống")
    private BigDecimal unitPrice;

    @NotNull(message = "Thành tiền không được để trống")
    private BigDecimal amount;

    private String meterImageUrl;

    /** Token admin override khi không chụp được ảnh (công tơ hỏng / không vào được phòng). */
    private java.util.UUID overrideToken;

    private String overrideReason;
}
