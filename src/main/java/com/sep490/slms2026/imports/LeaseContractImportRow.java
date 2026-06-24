package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class LeaseContractImportRow {

    private int rowNumber;
    private String contractCode;
    private String propertyName;
    private String address;
    private String district;
    private String province;
    private Double areaSize;
    private Integer totalFloor;
    private Integer totalRooms;
    private String ownerName;
    private BigDecimal totalRentAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String descriptions;
}
