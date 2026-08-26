package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.UpdatePricingConfigRequest;
import com.sep490.slms2026.dto.response.PricingConfigResponse;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.PricingConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminPricingController {

    private final PricingConfigService pricingConfigService;

    @GetMapping("/pricing-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<PricingConfigResponse> getPricingConfig() {
        return ResponseEntity.ok(pricingConfigService.get());
    }

    @PutMapping("/pricing-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<PricingConfigResponse> updatePricingConfig(
            @Valid @RequestBody UpdatePricingConfigRequest request) {
        return ResponseEntity.ok(pricingConfigService.update(
                request, SecurityUtils.requireCurrentUser().getId()));
    }
}
