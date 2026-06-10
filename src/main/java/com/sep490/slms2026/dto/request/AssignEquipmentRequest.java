package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignEquipmentRequest {

    @NotNull
    private Long catalogId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    private EquipmentStatus status;

    private Long roomId;

    private HouseArea houseArea;
}
