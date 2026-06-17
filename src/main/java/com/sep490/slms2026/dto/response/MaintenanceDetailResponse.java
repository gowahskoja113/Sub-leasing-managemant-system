package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;

import java.math.BigDecimal;
import java.util.List;

public class MaintenanceDetailResponse {
    private Long id;

    private String requestCode;

    private String tenantName;

    private String roomName;

    private MaintenanceCategory category;

    private MaintenancePriority priority;

    private MaintenanceStatus status;

    private String description;

    private List<String> images;

    private String resolutionNote;

    private BigDecimal totalCost;

    public void setId(Long id) {
    }
}
