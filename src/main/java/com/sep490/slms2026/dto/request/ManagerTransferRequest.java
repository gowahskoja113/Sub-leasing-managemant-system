package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ManagerTransferRequest {

    @NotNull(message = "managerId không được để trống")
    private UUID managerId;

    @NotNull(message = "toZoneId không được để trống")
    private UUID toZoneId;

    /** Khu vực gỡ quản lý này (chuyển hẳn, không kiêm nhiệm). Bỏ qua toZoneId nếu trùng. */
    private List<UUID> releaseZoneIds;
}
