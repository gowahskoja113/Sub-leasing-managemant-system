package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class MaintenanceResolveRequest {

    private String actionTaken;     // Mô tả công việc đã làm

    private BigDecimal cost;        // Chi phí thực tế

    private String status;          // IN_PROGRESS | RESOLVED | CANCELLED

    // Ảnh sau khi hoàn thành (AFTER) — danh sách URL
    private List<String> afterPhotoUrls;
}