package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.TenantContractDetailResponse.EquipmentItem;
import com.sep490.slms2026.dto.response.TenantContractDocumentResponse;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.TenantContractDocumentService;
import com.sep490.slms2026.service.TenantOnboardingService;
import com.sep490.slms2026.dto.request.ConfirmContractRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    /** GET / — danh sách HĐ (vd list DRAFT). */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<java.util.List<TenantContractResponse>> listAll(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(tenantOnboardingService.getContractsByStatus(status));
    }

    /** GET /{id} — xem chi tiết HĐ (manager hoặc khách thuê của HĐ đó). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','TENANT')")
    public ResponseEntity<TenantContractResponse> get(@PathVariable Long id) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(tenantContractDocumentService.getContractForUser(
                id, user.getId(), role));
    }

    /**
     * GET /{id}/available-equipments — thiết bị ACTIVE trong phạm vi HĐ (checkbox chọn bàn giao).
     */
    @GetMapping("/{id}/available-equipments")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<java.util.List<EquipmentItem>> getAvailableEquipments(@PathVariable Long id) {
        TenantContractResponse contract = tenantOnboardingService.getContract(id);
        return ResponseEntity.ok(contract.getAvailableEquipmentList());
    }

    /**
     * POST /{id}/document — trả URL file HĐ đã lưu (ưu tiên draftContractFileUrl).
     * Không render/lưu file mới trên BE.
     */
    @PostMapping("/{id}/document")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractDocumentResponse> generateDocument(@PathVariable Long id) {
        return ResponseEntity.ok(tenantContractDocumentService.generateAndStore(id));
    }

    /**
     * POST /{id}/draft-document — render DOCX từ tenant-apartment-draft-template (chỉ DRAFT).
     * FE nhận file → upload Cloudinary → PUT draftContractFileUrl.
     */
    @PostMapping("/{id}/draft-document")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<byte[]> generateDraftDocument(@PathVariable Long id) {
        TenantContractResponse contract = tenantContractDocumentService.getContractForUser(
                id,
                SecurityUtils.requireCurrentUser().getId(),
                SecurityUtils.requireCurrentUser().getAuthorities().iterator().next().getAuthority());
        byte[] docx = tenantContractDocumentService.renderDraftDocument(id);
        String filename = "DRAFT-" + contract.getContractCode() + ".docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    /** GET /{id}/document — lấy URL file HĐ (ưu tiên draftContractFileUrl trên Cloudinary). */
    @GetMapping("/{id}/document")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','TENANT')")
    public ResponseEntity<TenantContractDocumentResponse> getDocument(@PathVariable Long id) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        tenantContractDocumentService.getContractForUser(id, user.getId(), role);
        return ResponseEntity.ok(tenantContractDocumentService.getDocument(id));
    }

    /**
     * GET /{id}/document/download — tải/xem file DOCX đã lưu (qua JWT, không cần mở URL Cloudinary trực tiếp).
     * FE: fetch → blob → mở tab mới / share / Office viewer.
     */
    @GetMapping("/{id}/document/download")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','TENANT')")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        TenantContractResponse contract = tenantContractDocumentService.getContractForUser(
                id, user.getId(), role);
        byte[] docx = tenantContractDocumentService.downloadContractDocument(id, user.getId(), role);
        String filename = contract.getContractCode() + ".docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
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
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(tenantOnboardingService.getManagedContracts(status));
    }

    /** PUT /{id} — Cập nhật thông tin hợp đồng nháp. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> updateDraftContract(
            @PathVariable Long id,
            @Valid @RequestBody com.sep490.slms2026.dto.request.UpdateDraftContractRequest request) {
        return ResponseEntity.ok(tenantOnboardingService.updateDraftContract(id, request));
    }

    /** PATCH /{id}/assign-manager — Gán quản lý đón khách. */
    @PatchMapping("/{id}/assign-manager")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> assignManager(
            @PathVariable Long id,
            @Valid @RequestBody com.sep490.slms2026.dto.request.AssignManagerRequest request) {
        return ResponseEntity.ok(tenantOnboardingService.assignManager(id, request));
    }

    /** POST /{id}/resubmit-approval — chỉnh giá & gửi duyệt lại. */
    @PostMapping("/{id}/resubmit-approval")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> resubmitApproval(
            @PathVariable Long id,
            @Valid @RequestBody com.sep490.slms2026.dto.request.ResubmitApprovalRequest request) {
        return ResponseEntity.ok(tenantOnboardingService.resubmitApproval(id, request));
    }

    /** POST /{id}/cancel — hủy hợp đồng chưa ACTIVE (DRAFT / PENDING). */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> cancelContract(@PathVariable Long id) {
        tenantOnboardingService.cancelContract(id);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /{id}/terminate — thanh lý HĐ đang ACTIVE / EXPIRED (trả phòng sớm, vi phạm, thỏa thuận).
     */
    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> terminateActiveContract(
            @PathVariable Long id,
            @Valid @RequestBody com.sep490.slms2026.dto.request.TerminateContractRequest request) {
        return ResponseEntity.ok(tenantOnboardingService.terminateActiveContract(id, request));
    }
}
