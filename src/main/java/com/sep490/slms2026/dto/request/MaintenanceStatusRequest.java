package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.MaintenanceStatus;
import lombok.Data;

@Data
public class MaintenanceStatusRequest {
    private MaintenanceStatus status;
    private String onHoldReason;
    private String note;
}
