package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.MaintenanceRequestDto;
import com.sep490.slms2026.dto.request.MaintenanceResolveRequest;
import com.sep490.slms2026.dto.response.MaintenanceResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    // 📲 Tenant/Guest gửi yêu cầu bảo trì (từ app hoặc quét QR)
    // Không cần auth cứng — tenant hoặc anonymous từ QR scan đều gửi được
    @PreAuthorize("hasAnyAuthority('ROLE_TENANT', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @PostMapping("/request")
    public ResponseEntity<MaintenanceResponse> submitRequest(
            @RequestBody MaintenanceRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(maintenanceService.submitRequest(dto, userDetails.getId()));
    }

    // 📋 Tenant xem danh sách request của mình
    @PreAuthorize("hasAuthority('ROLE_TENANT')")
    @GetMapping("/my-requests")
    public ResponseEntity<Page<MaintenanceResponse>> getMyRequests(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(maintenanceService.getMyRequests(userDetails.getId(), status, pageable));
    }

    // 📋 Manager xem tất cả request trong vùng mình quản lý
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping
    public ResponseEntity<Page<MaintenanceResponse>> getRequestsForManager(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(maintenanceService.getRequestsForManager(userDetails.getId(), pageable));
    }

    // 🔍 Chi tiết request (kèm history + photos)
    @PreAuthorize("hasAnyAuthority('ROLE_TENANT', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceResponse> getDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(maintenanceService.getRequestDetail(id, userDetails.getId()));
    }

    // ✅ Manager/Staff cập nhật trạng thái, ghi lịch sử, đính kèm ảnh AFTER
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<MaintenanceResponse> resolveRequest(
            @PathVariable UUID id,
            @RequestBody MaintenanceResolveRequest resolveRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(maintenanceService.resolveRequest(id, resolveRequest, userDetails.getId()));
    }
}