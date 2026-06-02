package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class RoomRequest {
    private String roomNumber;
    private BigDecimal price;
    private BigDecimal deposit;
    private Double area;
    private Integer maxOccupants;
}