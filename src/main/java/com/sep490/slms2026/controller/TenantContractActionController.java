package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.service.TenantOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Các thao tác trên hợp đồng thuê theo ID (thanh toán cọc, xác nhận, xem trạng thái).
 */
@RestController
@RequestMapping("/api/v1/tenant-contracts")
@RequiredArgsConstructor
public class TenantContractActionController {

    private final TenantOnboardingService tenantOnboardingService;

    /** GET /{id} — xem chi tiết/trạng thái HĐ (mobile poll trạng thái thanh toán). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(tenantOnboardingService.getContract(id));
    }

    /** POST /{id}/deposit-payment — tạo link/QR thanh toán cọc qua PayOS. */
    @PostMapping("/{id}/deposit-payment")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> createDepositPayment(@PathVariable Long id) {
        return ResponseEntity.ok(tenantOnboardingService.createDepositPayment(id));
    }

    /** POST /{id}/check-payment — chủ động hỏi PayOS & đồng bộ trạng thái thanh toán. */
    @PostMapping("/{id}/check-payment")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> checkPayment(@PathVariable Long id) {
        return ResponseEntity.ok(tenantOnboardingService.syncPaymentStatus(id));
    }

    /** POST /{id}/confirm — hoàn tất HĐ sau khi đã thanh toán cọc + OTP. */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(tenantOnboardingService.confirmContract(id));
    }
}
