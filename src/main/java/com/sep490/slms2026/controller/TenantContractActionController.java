package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.TenantContractDocumentResponse;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.TenantContractDocumentService;
import com.sep490.slms2026.service.TenantOnboardingService;
import com.sep490.slms2026.dto.request.ConfirmContractRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Các thao tác trên hợp đồng thuê theo ID (thanh toán cọc, xác nhận, xuất file, xem trạng thái).
 */
@RestController
@RequestMapping("/api/v1/tenant-contracts")
@RequiredArgsConstructor
public class TenantContractActionController {

    private final TenantOnboardingService tenantOnboardingService;
    private final TenantContractDocumentService tenantContractDocumentService;

    /** GET /{id} — xem chi tiết HĐ (manager hoặc khách thuê của HĐ đó). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','TENANT')")
    public ResponseEntity<TenantContractResponse> get(@PathVariable Long id) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(tenantContractDocumentService.getContractForUser(
                id, user.getId(), role));
    }

    /** POST /{id}/document — xuất DOCX, lưu storage, trả URL (manager/admin). */
    @PostMapping("/{id}/document")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractDocumentResponse> generateDocument(@PathVariable Long id) {
        return ResponseEntity.ok(tenantContractDocumentService.generateAndStore(id));
    }

    /** GET /{id}/document — lấy URL file đã lưu (manager hoặc khách thuê). */
    @GetMapping("/{id}/document")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','TENANT')")
    public ResponseEntity<TenantContractDocumentResponse> getDocument(@PathVariable Long id) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        tenantContractDocumentService.getContractForUser(id, user.getId(), role);
        return ResponseEntity.ok(tenantContractDocumentService.getDocument(id));
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

    /** POST /{id}/send-otp — gửi OTP SMS tới SĐT khách thuê (Twilio). */
    @PostMapping("/{id}/send-otp")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Map<String, Object>> sendOtp(@PathVariable Long id) {
        tenantOnboardingService.sendContractConfirmOtp(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã gửi mã OTP tới số điện thoại khách thuê"));
    }

    /** POST /{id}/confirm — hoàn tất HĐ sau khi đã thanh toán cọc + OTP. */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> confirm(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmContractRequest request) {
        return ResponseEntity.ok(tenantOnboardingService.confirmContract(id, request.getOtp()));
    }

    /** GET /managed — danh sách hợp đồng chờ xử lý của manager. */
    @GetMapping("/managed")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<java.util.List<TenantContractResponse>> getManagedContracts(
            @RequestParam(required = false) com.sep490.slms2026.enums.PriceApprovalStatus status) {
        return ResponseEntity.ok(tenantOnboardingService.getManagedContracts(status));
    }

    /** POST /{id}/resubmit-approval — chỉnh giá & gửi duyệt lại. */
    @PostMapping("/{id}/resubmit-approval")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> resubmitApproval(
            @PathVariable Long id,
            @Valid @RequestBody com.sep490.slms2026.dto.request.ResubmitApprovalRequest request) {
        return ResponseEntity.ok(tenantOnboardingService.resubmitApproval(id, request));
    }

    /** POST /{id}/cancel — hủy hợp đồng đang chờ. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> cancelContract(@PathVariable Long id) {
        tenantOnboardingService.cancelContract(id);
        return ResponseEntity.ok().build();
    }
}
