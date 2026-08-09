package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CheckoutDisputeRequest;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.CheckoutProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/me/checkout-requests/{id}/settlement")
@RequiredArgsConstructor
public class TenantCheckoutProcessController {

    private final CheckoutProcessService checkoutProcessService;

    @PostMapping("/accept")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<Void> acceptSettlement(@PathVariable Long id) {
        checkoutProcessService.acceptSettlement(id, currentUserId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dispute")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<Void> disputeSettlement(@PathVariable Long id, @RequestBody CheckoutDisputeRequest request) {
        checkoutProcessService.disputeSettlement(id, currentUserId(), request);
        return ResponseEntity.ok().build();
    }

    private static UUID currentUserId() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return user.getId();
    }
}
