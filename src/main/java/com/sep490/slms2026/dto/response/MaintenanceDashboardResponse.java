package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class MaintenanceDashboardResponse {
    private long total;
    private long pending;
    private long inProgress;
    private long resolved;
    private long cancelled;
    private BigDecimal totalRepairCost;
}
