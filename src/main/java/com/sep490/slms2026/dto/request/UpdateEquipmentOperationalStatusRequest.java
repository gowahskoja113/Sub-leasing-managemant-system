package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.EquipmentOperationalStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEquipmentOperationalStatusRequest {

    @NotNull(message = "operationalStatus không được để trống")
    private EquipmentOperationalStatus operationalStatus;

    private String reason;
}
