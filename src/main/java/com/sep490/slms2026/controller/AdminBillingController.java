package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.UpdateBillingConfigRequest;
import com.sep490.slms2026.dto.response.AdminHostDto;
import com.sep490.slms2026.dto.response.BillingConfigResponse;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.AdminBillingService;
import com.sep490.slms2026.service.BillingConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.sep490.slms2026.service.BillingCronService;
import java.util.Map;

import com.sep490.slms2026.dto.response.AdminDepositDto;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingController {

    private final AdminBillingService adminBillingService;
    private final BillingCronService billingCronService;
    private final BillingConfigService billingConfigService;

    @GetMapping("/billing-config")
    public ResponseEntity<BillingConfigResponse> getBillingConfig() {
        return ResponseEntity.ok(billingConfigService.get());
    }

    @PutMapping("/billing-config")
    public ResponseEntity<BillingConfigResponse> updateBillingConfig(
            @Valid @RequestBody UpdateBillingConfigRequest request) {
        return ResponseEntity.ok(billingConfigService.update(request, SecurityUtils.requireCurrentUser().getId()));
    }

    @PostMapping("/billing/run-daily-sweep")
    public ResponseEntity<Map<String, Integer>> runDailySweep() {
        return ResponseEntity.ok(billingCronService.runDailySweep());
    }

    @GetMapping("/deposits")
    public ResponseEntity<Page<AdminDepositDto>> listDeposits(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminBillingService.getAdminDeposits(status, PageRequest.of(page, size)));
    }

    @GetMapping("/hosts")
    public ResponseEntity<List<AdminHostDto>> listHosts() {
        return ResponseEntity.ok(adminBillingService.getAdminHosts());
    }
}
