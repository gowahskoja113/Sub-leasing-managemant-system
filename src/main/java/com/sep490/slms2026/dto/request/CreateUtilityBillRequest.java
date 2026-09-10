package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateUtilityBillRequest {

    @NotNull
    private Long propertyId;

    /** ELECTRIC | ELECTRICITY | WATER */
    @NotBlank
    private String type;

    @NotBlank
    private String billingPeriod;

    @NotNull
    private Integer month;

    @NotNull
    private Integer year;

    /** Tổng kWh (điện) hoặc m³ (nước). */
    @NotNull
    @Positive
    private Integer totalQuantity;

    @NotNull
    @Positive
    private BigDecimal totalAmount;

    private String imageUrl;

    /** Chỉ số cũ in trên giấy EVN/nước — bắt buộc với nhà nguyên căn. */
    private BigDecimal prevReading;

    /** Chỉ số mới in trên giấy EVN/nước — bắt buộc với nhà nguyên căn. */
    private BigDecimal newReading;

    /**
     * Mã khách hàng trên giấy (EVN/nước) — sau bước confirm OCR trên FE.
     * BE đối chiếu với mã đã lưu trên property khi property đã có mã tương ứng.
     */
    private String customerCode;

    /**
     * true = admin đã confirm/sửa 3 field OCR (mã KH, chỉ số cũ, chỉ số mới) trước khi publish.
     * Không bỏ qua đối chiếu mã KH — chỉ đánh dấu đã qua bước review.
     */
    private Boolean ocrConfirmed;
}
