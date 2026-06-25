package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record HostDashboardSummaryResponse(
        String month,
        Finance finance,
        Occupancy occupancy,
        Counts counts
) {
    @Builder
    public record Finance(
            BigDecimal revenue,
            BigDecimal expense,
            BigDecimal netProfit,
            BigDecimal revenueChangePct,
            BigDecimal expenseChangePct,
            BigDecimal netProfitChangePct
    ) {
    }

    @Builder
    public record Occupancy(
            long totalRooms,
            long occupiedRooms,
            long vacantRooms,
            long maintenanceRooms,
            BigDecimal occupancyRate
    ) {
    }

    @Builder
    public record Counts(
            long pendingContracts,
            long openMaintenance,
            long activeManagers,
            long expiringMasterLeases,
            long outstandingInvoices,
            BigDecimal outstandingAmount
    ) {
    }
}
