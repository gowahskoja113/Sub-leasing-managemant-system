package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceDashboardResponse {
    private long total;
    private long pending;
    private long inProgress;
    private long resolved;
    private long cancelled;
    private BigDecimal totalRepairCost;
}
