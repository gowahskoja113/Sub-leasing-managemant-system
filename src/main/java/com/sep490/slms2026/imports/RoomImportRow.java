package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomImportRow {

    private int rowNumber;
    private String contractCode;
    private String roomNumber;
    private Integer floor;
    private Double area;
    private String note;
}
