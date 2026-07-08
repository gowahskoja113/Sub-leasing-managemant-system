package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PropertyCreateRequest {
    private String propertyName;
    private String address;
    private String descriptions;
    private UUID zoneId;
    private Boolean wholeHouse;
    private Double areaSize;
    private Double length;
    private Double width;
    private Integer totalFloor;
    private Integer totalRooms;
    private Long createdBy;
    /** @deprecated dùng createdBy */
    private Long managedBy;
    private List<String> imageUrls;
}
