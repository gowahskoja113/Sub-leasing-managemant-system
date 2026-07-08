package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.HostDashboardSummaryResponse;
import com.sep490.slms2026.service.HostDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// @RestController
// @RequestMapping("/api/v1/host/dashboard")
@RequiredArgsConstructor
public class HostDashboardController {

    private final HostDashboardService service;

    @GetMapping("/summary")
    public HostDashboardSummaryResponse getDashboardSummary(@RequestParam String month) {
        return service.getDashboardSummary(month);
    }
}
