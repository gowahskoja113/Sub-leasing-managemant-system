package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PropertyType;
import com.sep490.slms2026.enums.RoomStatus;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponse {

    private Long id;
    private Long propertyId;
    private String propertyName;    // tiện cho FE hiển thị, khỏi gọi thêm API
    private String roomNumber;
    private Integer floor;
    private BigDecimal price;
    private BigDecimal deposit;
    private Double area;
    private Integer maxOccupants;
    private String structureDescription;
    private String imageUrls;
    private PropertyType propertyType;
    private RoomStatus status;
    private String electricMeterCode;
    private String waterMeterCode;
}
