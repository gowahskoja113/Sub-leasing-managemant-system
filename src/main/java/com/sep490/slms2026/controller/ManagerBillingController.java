package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.RejectPaymentClaimRequest;
import com.sep490.slms2026.dto.response.ManagerInvoiceResponse;
import com.sep490.slms2026.dto.response.ManagerPaymentResponse;
import com.sep490.slms2026.dto.response.RentInvoiceSummaryResponse;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.ManagerBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ManagerBillingController {

    private final ManagerBillingService managerBillingService;

    @GetMapping("/api/v1/properties/{propertyId}/rent-invoices")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<RentInvoiceSummaryResponse>> listPropertyRentInvoices(
            @PathVariable Long propertyId,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(managerBillingService.listRentInvoices(propertyId, month));
    }

    @GetMapping("/api/v1/manager/invoices")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<ManagerInvoiceResponse>> listInvoices(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean isAdmin = isAdmin(user);
        return ResponseEntity.ok(managerBillingService.listInvoices(
                user.getId(), isAdmin, period, status, type));
    }

    @GetMapping("/api/v1/manager/payments")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<ManagerPaymentResponse>> listPayments(
            @RequestParam(required = false) String status) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean isAdmin = isAdmin(user);
        return ResponseEntity.ok(managerBillingService.listPayments(user.getId(), isAdmin, status));
    }

    @PostMapping("/api/v1/manager/payments/{id}/verify")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ManagerPaymentResponse> verifyPayment(@PathVariable Long id) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(managerBillingService.verifyPayment(
                user.getId(), isAdmin(user), id));
    }

    @PostMapping("/api/v1/manager/payments/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ManagerPaymentResponse> rejectPayment(
            @PathVariable Long id,
            @RequestBody(required = false) RejectPaymentClaimRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(managerBillingService.rejectPayment(
                user.getId(), isAdmin(user), id, reason));
    }

    private static boolean isAdmin(CustomUserDetails user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()));
    }
}
