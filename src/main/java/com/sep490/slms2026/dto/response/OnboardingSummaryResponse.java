package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PropertyStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingSummaryResponse {

    private Long propertyId;
    private PropertyStatus status;
    private Boolean wholeHouse;
    private Boolean hasRenovation;
    private Integer floorCount;
    private Integer roomsPerFloor;
    private Integer totalRooms;
    private boolean renovationCompleted;
    private LocalDate renovationStartDate;
    private LocalDate renovationEndDate;
    private LocalDateTime submittedToHostAt;
    private List<EquipmentManifestResponse> equipmentManifest;
    private List<RenovationLineResponse> renovationLines;
    private BigDecimal totalRenovationCost;
    private InboundContractResponse inboundContract;
    private DepreciationCalculationResponse pricing;
}
