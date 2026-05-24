package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PropertyRequest {
    private String title;
    private String description;
    private String address;
    private boolean isWholeHouse;
    private Integer totalRooms;
    private BigDecimal electricityPrice;
    private BigDecimal waterPrice;
    private String imageUrls;
    private UUID zoneId;

    private String authorizedOwnerName;

    private List<RoomRequest> rooms;
}