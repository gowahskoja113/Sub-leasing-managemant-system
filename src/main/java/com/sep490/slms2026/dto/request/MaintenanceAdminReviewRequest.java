package com.sep490.slms2026.dto.request;

import lombok.Data;

@Data
public class MaintenanceAdminReviewRequest {
    private Boolean approved;
    private String note;
}
