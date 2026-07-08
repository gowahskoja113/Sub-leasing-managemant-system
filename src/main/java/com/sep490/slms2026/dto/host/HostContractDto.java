package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record HostContractDto(
        String id,
        Long propertyId,
        String code,
        String lesseeName,
        String tenantPhone,
        String tenantCccd,
        String propertyName,
        String roomCode,
        String lessorName,
        BigDecimal rentAmount,
        BigDecimal deposit,
        LocalDate moveInDate,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String equipmentSnapshot
) {
}
