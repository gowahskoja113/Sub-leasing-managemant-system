package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateCheckoutRequest;
import com.sep490.slms2026.dto.response.CheckoutRequestResponse;
import com.sep490.slms2026.dto.response.TenantHandoverResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.TenantCheckoutService;
import com.sep490.slms2026.service.TenantHandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/me")
@RequiredArgsConstructor
public class TenantMeLifecycleController {

    private final TenantCheckoutService tenantCheckoutService;
    private final TenantHandoverService tenantHandoverService;

    @PostMapping("/checkout-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<CheckoutRequestResponse> createCheckoutRequest(
            @Valid @RequestBody CreateCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantCheckoutService.createRequest(currentUserId(), request));
    }

    @GetMapping("/checkout-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<List<CheckoutRequestResponse>> listCheckoutRequests() {
        return ResponseEntity.ok(tenantCheckoutService.listRequests(currentUserId()));
    }

    @GetMapping("/checkout-requests/{id}")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<CheckoutRequestResponse> getCheckoutRequest(@PathVariable Long id) {
        return ResponseEntity.ok(tenantCheckoutService.getRequest(currentUserId(), id));
    }

    @GetMapping("/handover")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<TenantHandoverResponse> getHandover() {
        return ResponseEntity.ok(tenantHandoverService.getHandover(currentUserId()));
    }

    @PostMapping("/handover/acknowledge")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<TenantHandoverResponse> acknowledgeHandover() {
        return ResponseEntity.ok(tenantHandoverService.acknowledgeHandover(currentUserId()));
    }

    private static UUID currentUserId() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return user.getId();
    }
}
