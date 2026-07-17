package com.sep490.slms2026.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class MaintenanceCreateRequest {
    private Long roomId;
    private Long equipmentId;
    /** Tiêu đề sự cố — hiển thị trên list/detail cho tenant và manager */
    private String title;
    private String description;
    private List<String> images;
}
