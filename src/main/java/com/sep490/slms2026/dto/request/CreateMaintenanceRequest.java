package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import lombok.Getter;
import lombok.Setter;

import org.apache.tomcat.util.http.parser.Priority;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Getter
@Setter
public class CreateMaintenanceRequest {
    private UUID roomId;
    private UUID propertyId;
    private MaintenanceCategory category;
    private MaintenancePriority priority;
    private String description;
    private List<String> imageUrls;
}
