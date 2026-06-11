package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardResponse {

    private Long totalProperties;

    private Long totalRooms;

    private Long wholeHouseCount;

    private Long roomBasedPropertyCount;

    private Double totalArea;

    private BigDecimal totalInboundCost;
}