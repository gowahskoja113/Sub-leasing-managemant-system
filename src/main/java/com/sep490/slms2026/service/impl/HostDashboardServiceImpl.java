package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.HostDashboardSummaryResponse;
import com.sep490.slms2026.service.HostDashboardService;
import org.springframework.stereotype.Service;

@Service
public class HostDashboardServiceImpl implements HostDashboardService {

    @Override
    public HostDashboardSummaryResponse getDashboardSummary(String month) {
        HostDashboardSummaryResponse response = new HostDashboardSummaryResponse();
        response.setMonth(month);
        
        HostDashboardSummaryResponse.Finance finance = new HostDashboardSummaryResponse.Finance();
        // Placeholder stats
        response.setFinance(finance);
        
        HostDashboardSummaryResponse.Occupancy occupancy = new HostDashboardSummaryResponse.Occupancy();
        response.setOccupancy(occupancy);
        
        HostDashboardSummaryResponse.Counts counts = new HostDashboardSummaryResponse.Counts();
        response.setCounts(counts);
        
        return response;
    }
}
