package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    @Data
    @Builder
    public static class EquipmentItem {
        private Long id;
        private String name;
        private String condition;
        private Integer quantity;
    }
}
