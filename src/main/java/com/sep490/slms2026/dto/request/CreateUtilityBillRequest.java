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
}
