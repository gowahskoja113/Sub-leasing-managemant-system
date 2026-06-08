package com.sep490.slms2026.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PropertyResponse {
    private Long id;
    private String propertyName;
    private String shortAddress; // Số nhà, tên đường
    private String fullAddress;  // Số nhà + Tên Quận + Tên Tỉnh
    private String descriptions;
    private UUID zoneId;
    private String zoneName;
    private Double areaSize;
    private Boolean wholeHouse;
    private Integer totalRooms;
    private String status;
    private BigDecimal price;
    private BigDecimal deposit;
}