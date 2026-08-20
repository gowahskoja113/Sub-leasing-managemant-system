package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.EquipmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEquipmentStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private EquipmentStatus status;
}
