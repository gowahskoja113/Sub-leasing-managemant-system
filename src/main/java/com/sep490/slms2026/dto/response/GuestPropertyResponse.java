package com.sep490.slms2026.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class GuestPropertyResponse {
    private Long id;
    private String propertyName;
    private String shortAddress;
    private String fullAddress;
    private String descriptions;
    private UUID zoneId;
    private String zoneName;
    private Double areaSize;
    private Double length;
    private Double width;
    private Boolean wholeHouse;
    private Boolean hasRenovation;
    private Integer totalFloor;
    private Integer totalRooms;
    private String status;
    private BigDecimal price;
    private boolean renovationCompleted;
    private List<String> imageUrls;

    private Double latitude;
    private Double longitude;
    private List<String> amenities;
    private BigDecimal electricityUnitPrice;
    private BigDecimal waterUnitPrice;
    private Integer depositMonths;
    private BigDecimal serviceFee;

    /** true nếu BĐS còn phòng/căn cho thuê */
    private Boolean rentalAvailable;
}
