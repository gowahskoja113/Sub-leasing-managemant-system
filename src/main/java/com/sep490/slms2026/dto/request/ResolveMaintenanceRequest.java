package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveMaintenanceRequest {
    private Long repairCost;       // số nguyên VND
    private String resolutionNote;
}
