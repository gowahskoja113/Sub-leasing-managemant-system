package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateMaintenanceRequest;
import com.sep490.slms2026.dto.request.ResolveMaintenanceRequest;
import com.sep490.slms2026.dto.request.UpdateMaintenanceStatusRequest;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified Maintenance Controller — base path /api/v1/maintenance.
 * Phân quyền theo JWT role (ROLE_TENANT, ROLE_MANAGER, ROLE_ADMIN, ROLE_OWNER).
 */
@RestController
@RequestMapping("/api/v1/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;
    private final UserRepository userRepository;

    // ========== 2.1 Tenant ==========

    /** POST /api/v1/maintenance — tenant tạo yêu cầu */
    @PostMapping
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<MaintenanceRequestResponse> createRequest(
            @RequestBody CreateMaintenanceRequest dto,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        MaintenanceRequestResponse response = maintenanceService.createRequest(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /api/v1/maintenance/my-requests — tenant xem request của mình */
    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<Page<MaintenanceRequestResponse>> getMyRequests(
            @RequestParam(required = false) MaintenanceStatus status,
            Pageable pageable,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(maintenanceService.getMyRequests(userId, status, pageable));
    }

    // ========== 2.2 Operations Manager ==========

    /** PUT /api/v1/maintenance/{id}/status — OM đổi trạng thái + lịch hẹn */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<MaintenanceRequestResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateMaintenanceStatusRequest dto,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(maintenanceService.updateStatus(id, dto, userId));
    }

    /** PUT /api/v1/maintenance/{id}/resolve — OM hoàn tất sửa chữa */
    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<MaintenanceRequestResponse> resolveRequest(
            @PathVariable Long id,
            @RequestBody ResolveMaintenanceRequest dto,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(maintenanceService.resolveRequest(id, dto, userId));
    }

    // ========== 2.3 Admin/Owner + shared ==========

    /** GET /api/v1/maintenance/dashboard — Admin/Owner dashboard */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<MaintenanceDashboardResponse> getDashboard(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(maintenanceService.getDashboard(propertyId, from, to));
    }

    /** GET /api/v1/maintenance — OM/Admin xem tất cả (filter + paging) */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<Page<MaintenanceRequestResponse>> getAllRequests(
            @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(required = false) MaintenancePriority priority,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) MaintenanceCategory category,
            Pageable pageable,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(maintenanceService.getAllRequests(
                status, priority, propertyId, roomId, category, pageable, userId));
    }

    /** GET /api/v1/maintenance/{id} — xem chi tiết (mọi role có quyền) */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT', 'MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<MaintenanceRequestResponse> getRequestDetail(
            @PathVariable Long id,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(maintenanceService.getRequestById(id, userId));
    }

    // ========== HELPER ==========

    private UUID resolveUserId(Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + username));
        return user.getId();
    }
}
