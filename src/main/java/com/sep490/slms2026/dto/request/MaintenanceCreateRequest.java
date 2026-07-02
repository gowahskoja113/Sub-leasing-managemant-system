package com.sep490.slms2026.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class MaintenanceCreateRequest {
    private Long roomId;
    private Long equipmentId;
    private String category;
    private String priority;
    private String description;
    private List<String> images;
}
