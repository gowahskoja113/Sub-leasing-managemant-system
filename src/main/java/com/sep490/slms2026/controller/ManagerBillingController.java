package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ManagerPaymentQrRequest;
import com.sep490.slms2026.dto.request.RejectPaymentClaimRequest;
import com.sep490.slms2026.dto.response.ManagerPaymentQrResponse;
import com.sep490.slms2026.dto.response.ManagerInvoiceResponse;
import com.sep490.slms2026.dto.response.ManagerPaymentHistoryResponse;
import com.sep490.slms2026.dto.response.ManagerPaymentResponse;
import com.sep490.slms2026.dto.response.RentInvoiceSummaryResponse;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.ManagerBillingService;
import com.sep490.slms2026.service.TenantBillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.sep490.slms2026.dto.response.ManagerDepositDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@RestController
@RequiredArgsConstructor
public class ManagerBillingController {

    private final ManagerBillingService managerBillingService;
    private final TenantBillingService tenantBillingService;

    @GetMapping("/api/v1/properties/{propertyId}/rent-invoices")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<RentInvoiceSummaryResponse>> listPropertyRentInvoices(
            @PathVariable Long propertyId,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(managerBillingService.listRentInvoices(propertyId, month));
    }

    @GetMapping("/api/v1/manager/invoices")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    public ResponseEntity<List<ManagerInvoiceResponse>> listInvoices(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean isAdmin = isAdminOrOwner(user);
        return ResponseEntity.ok(managerBillingService.listInvoices(
                user.getId(), isAdmin, period, status, type));
    }

    /**
     * Chi tiết hoá đơn kèm items[] — hoá đơn cọc onboard trả dòng tiền cọc.
     * Admin / manager web dùng endpoint này thay vì nhồi items vào list.
     */
    @GetMapping("/api/v1/manager/invoices/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    public ResponseEntity<ManagerInvoiceResponse> getInvoice(@PathVariable Long id) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(managerBillingService.getInvoice(user.getId(), isAdminOrOwner(user), id));
    }

    @GetMapping("/api/v1/manager/payments")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    public ResponseEntity<List<ManagerPaymentResponse>> listPayments(
            @RequestParam(required = false) String status) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean isAdmin = isAdminOrOwner(user);
        return ResponseEntity.ok(managerBillingService.listPayments(user.getId(), isAdmin, status));
    }

    /**
     * Lịch sử thu thật từ {@code tenant_payments}. Khác {@code GET /manager/payments}
     * (hàng chờ đối soát claim).
     */
    @GetMapping("/api/v1/manager/payments/history")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    public ResponseEntity<Page<ManagerPaymentHistoryResponse>> listPaymentHistory(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean isAdmin = isAdminOrOwner(user);
        return ResponseEntity.ok(managerBillingService.listPaymentHistory(
                user.getId(), isAdmin, propertyId, contractId, from, to,
                PageRequest.of(page, size)));
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

    @GetMapping("/api/v1/manager/deposits")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Page<ManagerDepositDto>> listManagerDeposits(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(managerBillingService.getManagerDeposits(user.getId(), status, PageRequest.of(page, size)));
    }

    @PostMapping("/api/v1/manager/invoices/{id}/payment-qr")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ManagerPaymentQrResponse> createPaymentQr(
            @PathVariable Long id,
            @RequestBody @Valid ManagerPaymentQrRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(tenantBillingService.createManagerPaymentQr(user.getId(), id, request));
    }

    private static boolean isAdmin(CustomUserDetails user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()));
    }

    private static boolean isAdminOrOwner(CustomUserDetails user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()) || Role.ROLE_OWNER.name().equals(a.getAuthority()));
    }
}
