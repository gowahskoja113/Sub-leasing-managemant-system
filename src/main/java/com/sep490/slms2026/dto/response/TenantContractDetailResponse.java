package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TenantContractDetailResponse {
    private Long id;
    private String code;
    private String type;
    private String status;

    private String lessorName;
    private String lessorPhone;
    private String lesseeName;
    private String lesseeCccd;
    private String lesseePhone;

    private String propertyName;
    private String roomCode;
    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal rentAmount;
    private BigDecimal depositAmount;

    private List<EquipmentItem> equipmentList;

    private String notes;
    private LocalDateTime signedAt;
    private LocalDateTime terminatedAt;
    private String terminationReason;
    private String pdfUrl;

    private UUID assignedManagerId;
    private String assignedManagerName;
    private UUID onboardedByManagerId;
    private String onboardedByManagerName;
    private String onboardedByManagerPhone;
    private LocalDateTime onboardedAt;

    @Data
    @Builder
    public static class EquipmentItem {
        private Long id;
        private String name;
        private String condition;
        private Integer quantity;
        /** EXISTING = có sẵn trong nhà; ADDED = lắp thêm theo deal. */
        private String source;
        /** ROOM = thuộc phòng; SHARED = khu vực chung. */
        private String scope;
        private String roomNumber;
        private HouseArea houseArea;
        /** Chi phí (chỉ có khi source = ADDED). */
        private BigDecimal cost;
    }
}
