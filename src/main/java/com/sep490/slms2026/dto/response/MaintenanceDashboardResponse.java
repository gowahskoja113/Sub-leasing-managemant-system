package com.sep490.slms2026.dto.response;

import java.math.BigDecimal;

public class MaintenanceDashboardResponse {
    private Long totalRequests;

    private Long pendingRequests;

    private Long inProgressRequests;

    private Long resolvedRequests;

    private BigDecimal totalMaintenanceCost;
}
