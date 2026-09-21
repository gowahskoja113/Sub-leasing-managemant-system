package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MaintenanceReportFaultRequest {
    private String faultReason;
    /** Tuỳ chọn — không bắt buộc thêm ảnh khi xác định bên lỗi. */
    private List<String> faultEvidenceImages;
}
