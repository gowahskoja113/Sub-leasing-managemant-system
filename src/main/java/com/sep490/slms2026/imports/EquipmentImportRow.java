package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class EquipmentImportRow {

    private int rowNumber;
    private String contractCode;
    private String roomNumber;
    private String houseAreaRaw;
    private String catalogName;
    private String sourceRaw;
    private String statusRaw;
    private Integer quantity;
    private BigDecimal price;
    private String note;
}
