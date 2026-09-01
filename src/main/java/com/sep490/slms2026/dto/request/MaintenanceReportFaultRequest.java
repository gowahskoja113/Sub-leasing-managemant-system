package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MaintenanceReportFaultRequest {
    private String faultReason;
    private List<String> faultEvidenceImages;
}
