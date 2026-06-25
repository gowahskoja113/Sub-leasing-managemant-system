package com.sep490.slms2026.dto.host;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HostExpenseUpsertRequest {
    @NotBlank
    private String propertyId;

    @NotBlank
    private String category;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String month;

    private String note;
}
