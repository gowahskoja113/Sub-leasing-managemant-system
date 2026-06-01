package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class EquipmentRequest {

    private UUID roomId;        // nullable — có thể gán sau

    private String name;        // Tên thiết bị

    private String category;    // Loại: ELECTRICAL, PLUMBING, FURNITURE, HVAC, OTHER

    private LocalDate installedDate;

    private BigDecimal purchaseCost;

    private String description;

    // qrCode và qrPayload sẽ được sinh tự động ở service — không nhận từ client
}