package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ManagerPerformanceDTO {
    private Long managerId;
    private String managerName;
    private String phone;
    private Integer propertyCount;
    private Integer activeTenants;
    private Double occupancyRate;
    private Integer resolvedMaintenance;
    private Integer openMaintenance;
}
