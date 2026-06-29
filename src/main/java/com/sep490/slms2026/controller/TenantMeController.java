package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.dto.response.TenantPaymentResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.EquipmentService;
import com.sep490.slms2026.service.TenantBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/me")
@RequiredArgsConstructor
public class TenantMeController {

    private final TenantBillingService tenantBillingService;
    private final EquipmentService equipmentService;

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<List<TenantInvoiceResponse>> listInvoices(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(tenantBillingService.listInvoices(currentUserId(), status, type));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<TenantInvoiceResponse> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(tenantBillingService.getInvoice(currentUserId(), id));
    }

    @PostMapping("/invoices/{id}/payment")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<TenantInvoiceResponse> createPayment(@PathVariable Long id) {
        return ResponseEntity.ok(tenantBillingService.createPayment(currentUserId(), id));
    }

    @PostMapping("/invoices/{id}/payment/check")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<TenantInvoiceResponse> checkPayment(@PathVariable Long id) {
        return ResponseEntity.ok(tenantBillingService.checkPayment(currentUserId(), id));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<List<TenantPaymentResponse>> listPayments() {
        return ResponseEntity.ok(tenantBillingService.listPayments(currentUserId()));
    }

    @GetMapping("/equipments")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<List<EquipmentResponse>> myEquipments() {
        return ResponseEntity.ok(equipmentService.getEquipmentsForCurrentTenant());
    }

    private static UUID currentUserId() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return user.getId();
    }
}
