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
    private String shortAddress;
    private String fullAddress;
    private String descriptions;
    private UUID zoneId;
    private String zoneName;
    private Double areaSize;
    private Boolean wholeHouse;
    private Boolean hasRenovation;
    private Integer totalFloor;
    private Integer totalRooms;
    private String status;
    private BigDecimal price;
    private Long createdBy;
    private Long operationManagerId;
    private boolean renovationCompleted;
    private List<String> imageUrls;
}
