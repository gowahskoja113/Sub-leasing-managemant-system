package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.FaultResolutionPath;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MaintenanceRejectFaultRequest {
    private String faultReason;
    private List<String> faultEvidenceImages;
    private FaultResolutionPath resolutionPath;
    private LocalDate selfRepairDeadline;
    private BigDecimal estimatedDamageAmount;
}
