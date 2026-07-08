package com.sep490.slms2026.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class HostDashboardSummaryResponse {
    private String month;
    private Finance finance;
    private Occupancy occupancy;
    private Counts counts;

    @Data
    public static class Finance {
        private BigDecimal revenue;
        private BigDecimal expense;
        private BigDecimal netProfit;
        private Double revenueChangePct;
        private Double expenseChangePct;
        private Double netProfitChangePct;
    }

    @Data
    public static class Occupancy {
        private Integer totalRooms;
        private Integer occupiedRooms;
        private Integer vacantRooms;
        private Integer maintenanceRooms;
        private Double occupancyRate;
    }

    @Data
    public static class Counts {
        private Integer pendingContracts;
        private Integer openMaintenance;
        private Integer activeManagers;
        private Integer expiringMasterLeases;
        private Integer outstandingInvoices;
        private BigDecimal outstandingAmount;
    }
}
