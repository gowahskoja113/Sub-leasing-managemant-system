package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateEvnBillRequest {

    @NotNull
    private Long propertyId;

    @NotBlank
    private String billingPeriod;

    @NotNull
    private Integer month;

    @NotNull
    private Integer year;

    @NotNull
    @Positive
    private Integer totalKwh;

    @NotNull
    @Positive
    private BigDecimal totalAmount;

    private String imageUrl;
}
