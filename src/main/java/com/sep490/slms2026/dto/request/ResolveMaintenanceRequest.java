package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ResolveMaintenanceRequest {
    private String resolutionNote;
    private BigDecimal laborCost;
    private BigDecimal materialCost;
    private BigDecimal externalServiceCost;
    private List<Long> equipmentIds;
}
