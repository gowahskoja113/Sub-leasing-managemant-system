package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.IssueInvoiceRequest;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.dto.response.TenantPendingChargeResponse;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.TenantPendingChargeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/pending-charges")
@RequiredArgsConstructor
public class ManagerPendingChargeController {

    private final TenantPendingChargeService pendingChargeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<TenantPendingChargeResponse>> getPendingCharges(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) String status) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()));
        return ResponseEntity.ok(pendingChargeService.getPendingChargesForManager(user.getId(), isAdmin, propertyId, status));
    }

    @PostMapping("/issue-invoice")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<TenantInvoiceResponse> issueInvoiceFromCharges(
            @RequestParam Long contractId,
            @RequestBody IssueInvoiceRequest request) {
        return ResponseEntity.ok(pendingChargeService.issueInvoiceFromCharges(contractId, request));
    }
}
