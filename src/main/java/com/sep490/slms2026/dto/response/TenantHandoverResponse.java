package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.dto.response.TenantContractDetailResponse.EquipmentItem;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantHandoverResponse {
    private Long contractId;
    private String contractCode;
    private String propertyName;
    private String roomNumber;
    private BigDecimal initialElectricReading;
    private BigDecimal initialWaterReading;
    private String electricMeterImageUrl;
    private String waterMeterImageUrl;
    private java.time.LocalDateTime electricMeterCapturedAt;
    private java.time.LocalDateTime waterMeterCapturedAt;
    private List<String> roomConditionUrls;
    private List<ContractEvidencePhotoResponse> roomConditionPhotos;
    private String roomConditionNote;
    private String equipmentSnapshot;
    /** Thiết bị khách nhận bàn giao (có cấu trúc). */
    private List<EquipmentItem> equipmentList;
    private boolean acknowledged;
    private LocalDateTime acknowledgedAt;
}
