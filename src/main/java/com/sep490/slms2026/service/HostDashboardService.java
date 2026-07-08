package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.HostDashboardSummaryResponse;

public interface HostDashboardService {
    HostDashboardSummaryResponse getDashboardSummary(String month);
}
