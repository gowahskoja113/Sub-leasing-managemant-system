package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record HostManagerPerformanceRow(
        String managerId,
        String managerName,
        String phone,
        long propertyCount,
        long activeTenants,
        BigDecimal occupancyRate,
        long resolvedMaintenance,
        long openMaintenance
) {
}
