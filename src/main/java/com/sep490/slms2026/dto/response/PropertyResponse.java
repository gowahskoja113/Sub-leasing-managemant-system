package com.sep490.slms2026.dto.response;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PropertyResponse {
    private UUID id;
    private String title;
    private String description;
    private String address;
    private boolean isWholeHouse;
    private Integer totalRooms;
    private BigDecimal electricityPrice;
    private BigDecimal waterPrice;
    private String imageUrls;
    private UUID zoneId;
    private String zoneFullName;
    private UUID ownerId;
    private List<RoomResponse> rooms;
}