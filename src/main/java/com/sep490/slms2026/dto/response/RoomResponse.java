package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.RoomStatus;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class RoomResponse {
    private UUID id;
    private String roomNumber;
    private BigDecimal price;
    private BigDecimal deposit;
    private Double area;
    private RoomStatus status;
    private String imageUrls;
}