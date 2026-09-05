package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record MasterLeaseDto(
        String id,
        String propertyId,
        String ownerName,
        String contractCode,
        String contractScanUrl,
        BigDecimal monthlyRent,
        BigDecimal totalRentAmount,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {
}
