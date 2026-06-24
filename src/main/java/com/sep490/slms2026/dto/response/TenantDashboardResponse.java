package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TenantDashboardResponse {
    private RoomSummary room;
    private ContractSummary contract;
    private BuildingSummary building;
    private ActivitySummary summary;

    @Data
    @Builder
    public static class RoomSummary {
        private Long id;
        private String roomNumber;
        private Integer floor;
        private Double area;
        private BigDecimal depositAmount;
    }

    @Data
    @Builder
    public static class ContractSummary {
        private Long id;
        private String code;
        private LocalDate startDate;
        private LocalDate endDate;
        private Long daysLeft;
        private String status;
    }

    @Data
    @Builder
    public static class BuildingSummary {
        private Long propertyId;
        private String name;
        private String address;
        private Integer totalFloors;
        private BigDecimal electricityRate;
        private BigDecimal waterRate;
        private BigDecimal serviceCharge;
        private String hostName;
        private String hostPhone;
    }

    @Data
    @Builder
    public static class ActivitySummary {
        private Integer overdueInvoiceCount;
        private BigDecimal overdueTotal;
        private Integer maintenancePending;
        private Integer maintenanceInProgress;
        private Integer unreadNotifications;
    }
}
