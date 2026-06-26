package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record HostContractDto(
        String id,
        String code,
        String lesseeName,
        String propertyName,
        String roomCode,
        String lessorName,
        BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {
}
