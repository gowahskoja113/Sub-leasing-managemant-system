package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePropertyStructureRequest {

    @NotNull
    @Min(1)
    private Integer totalFloor;

    @NotNull
    @Min(1)
    private Integer totalRooms;
}
