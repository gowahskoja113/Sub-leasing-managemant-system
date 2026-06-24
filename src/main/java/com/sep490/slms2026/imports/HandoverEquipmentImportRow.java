package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HandoverEquipmentImportRow {

    private int rowNumber;
    private String contractCode;
    private String equipmentName;
    private String description;
    private String roomNumber;
    private String houseAreaRaw;
    private String statusRaw;
    private Integer quantity;
    private String note;
}
