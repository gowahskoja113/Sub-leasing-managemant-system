package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HandoverEquipmentResponse {

    private Long id;
    private Long catalogId;
    private String catalogName;
    private String description;
    private String roomNumber;
    private HouseArea houseArea;
    private EquipmentStatus status;
    private Integer quantity;
    private String note;
}
