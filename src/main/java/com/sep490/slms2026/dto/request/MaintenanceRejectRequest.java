package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MaintenanceRejectRequest {
    private String reason;
    /** URL ảnh minh chứng (optional nếu upload multipart kèm request). */
    private List<String> images;
}
