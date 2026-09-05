package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TenantDashboardResponse {
    /** HĐ đang chọn (primary) — tương thích FE cũ */
    private RoomSummary room;
    private ContractSummary contract;
    private BuildingSummary building;
    private ActivitySummary summary;

    /** Tất cả HĐ ACTIVE của account (để FE hiện picker khi > 1) */
    private List<ContractSummary> contracts;

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
        private Long propertyId;
        private String propertyName;
        private Long roomId;
        private String roomNumber;
        /** ROOM = thuê theo phòng; WHOLE_HOUSE = thuê nguyên căn (`room_id` null trên HĐ). */
        private String type;
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
        private String managerName;
        private String managerPhone;
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
