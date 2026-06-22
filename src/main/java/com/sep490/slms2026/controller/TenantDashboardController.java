package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.TenantDashboardResponse;
import com.sep490.slms2026.service.TenantDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/me/dashboard")
@RequiredArgsConstructor
public class TenantDashboardController {

    private final TenantDashboardService tenantDashboardService;

    @GetMapping
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<TenantDashboardResponse> getTenantDashboard() {
        return ResponseEntity.ok(tenantDashboardService.getDashboard());
    }
}
