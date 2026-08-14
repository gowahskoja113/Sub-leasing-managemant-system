package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignZoneManagerRequest {
    @NotNull(message = "managerId không được để trống")
    private UUID managerId;
}
