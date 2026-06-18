package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.HouseArea;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReassignEquipmentRequest {

    @NotNull
    private Long roomId;

    private HouseArea houseArea;
}
