package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record HostPropertyPerformanceRow(
        String propertyId,
        String propertyName,
        String address,
        BigDecimal occupancyRate,
        long occupiedRooms,
        long totalRooms,
        BigDecimal monthlyRevenue,
        long openMaintenance
) {
}
