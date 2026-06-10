package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AddRenovationLineRequest {

    @NotNull
    private Long categoryId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal cost;

    private String note;
}
