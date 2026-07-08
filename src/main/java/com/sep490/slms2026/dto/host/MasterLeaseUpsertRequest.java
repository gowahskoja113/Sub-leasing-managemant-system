package com.sep490.slms2026.dto.host;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MasterLeaseUpsertRequest {
    @NotBlank
    private String propertyId;

    @NotBlank
    private String ownerName;

    private String ownerPhone;

    @NotNull
    @Positive
    private BigDecimal monthlyRent;

    private BigDecimal deposit;

    private Integer paymentDay;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private BigDecimal escalationPct;
}
