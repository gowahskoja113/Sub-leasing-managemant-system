package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ApproveCheckoutRequest;
import com.sep490.slms2026.dto.request.CompleteCheckoutRequest;
import com.sep490.slms2026.dto.request.RejectCheckoutRequest;
import com.sep490.slms2026.dto.response.CheckoutRequestResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.TenantCheckoutService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout-requests")
@RequiredArgsConstructor
public class CheckoutRequestController {

    private final TenantCheckoutService tenantCheckoutService;

    /** GET / — danh sách yêu cầu trả phòng (manager/admin). */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<CheckoutRequestResponse>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(tenantCheckoutService.listRequestsForManager(status));
    }

    /** GET /{id} — chi tiết yêu cầu (manager/admin). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<CheckoutRequestResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(tenantCheckoutService.getRequestForManager(id));
    }

    /** POST /{id}/approve — duyệt yêu cầu (PENDING → APPROVED). */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<CheckoutRequestResponse> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveCheckoutRequest request) {
        return ResponseEntity.ok(tenantCheckoutService.approveRequest(id, currentUserId(), request));
    }

    /** POST /{id}/reject — từ chối yêu cầu (PENDING → REJECTED). */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<CheckoutRequestResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectCheckoutRequest request) {
        return ResponseEntity.ok(tenantCheckoutService.rejectRequest(id, currentUserId(), request));
    }

    /**
     * POST /{id}/complete — hoàn tất trả phòng (APPROVED → COMPLETED).
     * Đồng thời terminate HĐ + giải phóng phòng/thiết bị.
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<CheckoutRequestResponse> complete(
            @PathVariable Long id,
            @RequestBody(required = false) CompleteCheckoutRequest request) {
        return ResponseEntity.ok(tenantCheckoutService.completeRequest(id, currentUserId(), request));
    }

    private static UUID currentUserId() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return user.getId();
    }
}
