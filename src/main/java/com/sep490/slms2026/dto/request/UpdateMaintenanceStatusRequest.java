package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.MaintenanceStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateMaintenanceStatusRequest {
    private MaintenanceStatus status;
    private String note;
    private LocalDateTime scheduledDate;
}
