package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.DashboardResponse;
import com.sep490.slms2026.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/owner")
    public ResponseEntity<DashboardResponse> getHostDashboard() {

        return ResponseEntity.ok(
                dashboardService.getDashboard()
        );
    }
}