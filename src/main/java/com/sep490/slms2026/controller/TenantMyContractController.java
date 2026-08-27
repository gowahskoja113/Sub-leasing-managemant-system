package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ConfirmContractRequest;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.TenantContractDocumentService;
import com.sep490.slms2026.service.TenantOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Khách thuê xem / xác nhận hợp đồng của mình (kèm URL file, dual-OTP). */
@RestController
@RequestMapping({"/api/v1/me/tenant-contracts", "/api/v1/tenant/me/contracts"})
@RequiredArgsConstructor
public class TenantMyContractController {

    private final TenantContractDocumentService tenantContractDocumentService;
    private final TenantOnboardingService tenantOnboardingService;

    @GetMapping
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<List<TenantContractResponse>> myContracts() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(tenantContractDocumentService.listContractsForTenant(user.getId()));
    }

    /**
     * HĐ PENDING + PAID đang chờ tenant xác nhận — RootNavigator dùng để ép vào màn confirm.
     * 204 nếu không có.
     */
    @GetMapping("/pending-confirm")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<TenantContractResponse> pendingConfirm() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return tenantOnboardingService.findPendingConfirmForTenant(user.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Tenant bấm gửi OTP: sinh 2 mã (TENANT + MANAGER). */
    @PostMapping("/{id}/send-confirm-otp")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<Map<String, Object>> sendConfirmOtp(@PathVariable Long id) {
        tenantOnboardingService.sendDualContractConfirmOtps(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã gửi mã OTP xác nhận tới khách và quản lý"));
    }

    /** Tenant nhập OTP của mình. */
    @PostMapping("/{id}/confirm-otp")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<TenantContractResponse> confirmOtp(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmContractRequest request) {
        return ResponseEntity.ok(tenantOnboardingService.confirmContractByTenant(id, request.getOtp()));
    }
}
