package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PropertyPerformanceDTO {
    private Long propertyId;
    private String propertyName;
    private String address;
    private Double occupancyRate;
    private Integer occupiedRooms;
    private Integer totalRooms;
    private BigDecimal monthlyRevenue;
    private Integer openMaintenance;
}
