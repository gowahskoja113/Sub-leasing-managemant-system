package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateMaintenanceRequest {
    private Long roomId;
    private Long equipmentId;  // nullable — từ QR scan
    private MaintenanceCategory category;
    private MaintenancePriority priority;
    private String description;
    private List<String> images; // Cloudinary URLs
}
