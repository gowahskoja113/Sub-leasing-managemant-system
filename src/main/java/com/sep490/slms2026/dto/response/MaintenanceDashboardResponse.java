package com.sep490.slms2026.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceDashboardResponse {
    private long total;
    private long pending;
    private long inProgress;
    private long resolved;
    private long cancelled;
    private long totalRepairCost;
}
