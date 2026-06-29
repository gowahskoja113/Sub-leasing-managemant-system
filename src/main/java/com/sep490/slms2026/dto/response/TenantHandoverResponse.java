package com.sep490.slms2026.dto.response;

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
    private List<String> roomConditionUrls;
    private String roomConditionNote;
    private String equipmentSnapshot;
    private boolean acknowledged;
    private LocalDateTime acknowledgedAt;
}
