package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaintenanceResolveCostRequest {
    private String action;
    private BigDecimal repairCost;
    private String note;
}
