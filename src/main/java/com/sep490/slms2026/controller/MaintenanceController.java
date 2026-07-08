package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.*;
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

    // ========== 2.1 Tenant (HEAD) ==========

    /** POST /api/v1/maintenance — tenant tạo yêu cầu */
    @PostMapping("/feature-tenant")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<MaintenanceRequestResponse> createRequestFeature(
            @RequestBody CreateMaintenanceRequest dto,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        MaintenanceRequestResponse response = maintenanceService.createRequest(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /api/v1/maintenance/my-requests — tenant xem request của mình */
    @GetMapping("/feature-my-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<Page<MaintenanceRequestResponse>> getMyRequestsFeature(
            @RequestParam(required = false) MaintenanceStatus status,
            Pageable pageable,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(maintenanceService.getMyRequests(userId, status, pageable));
    }

    // ========== 2.2 Operations Manager (HEAD) ==========

    /** PUT /api/v1/maintenance/{id}/status-feature — OM đổi trạng thái + lịch hẹn */
    @PutMapping("/{id}/status-feature")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<MaintenanceRequestResponse> updateStatusFeature(
            @PathVariable Long id,
            @RequestBody UpdateMaintenanceStatusRequest dto,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(maintenanceService.updateStatus(id, dto, userId));
    }

    /** PUT /api/v1/maintenance/{id}/resolve-feature — OM hoàn tất sửa chữa */
    @PutMapping("/{id}/resolve-feature")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<MaintenanceRequestResponse> resolveRequestFeature(
            @PathVariable Long id,
            @RequestBody ResolveMaintenanceRequest dto,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(maintenanceService.resolveRequest(id, dto, userId));
    }

    // ========== 2.3 Admin/Owner + shared (HEAD) ==========

    /** GET /api/v1/maintenance/dashboard-feature — Admin/Owner dashboard */
    @GetMapping("/dashboard-feature")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<MaintenanceDashboardResponse> getDashboardFeature(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(maintenanceService.getDashboard(propertyId, from, to));
    }

    /** GET /api/v1/maintenance-feature — OM/Admin xem tất cả (filter + paging) */
    @GetMapping("/feature")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<Page<MaintenanceRequestResponse>> getAllRequestsFeature(
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

    /** GET /api/v1/maintenance/{id}/feature — xem chi tiết (mọi role có quyền) */
    @GetMapping("/{id}/feature")
    @PreAuthorize("hasAnyRole('TENANT', 'MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<MaintenanceRequestResponse> getRequestDetailFeature(
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

    // ========== Main Branch Methods ==========

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TENANT')")
    public Page<MaintenanceRequestResponse> getRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long roomId,
            Pageable pageable) {
        return maintenanceService.getRequests(status, priority, category, propertyId, roomId, pageable);
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT')")
    public MaintenanceRequestResponse createRequest(@RequestBody com.sep490.slms2026.dto.request.MaintenanceCreateRequest request) {
        return maintenanceService.createRequest(request);
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('TENANT')")
    public Page<MaintenanceRequestResponse> getMyRequests(Pageable pageable) {
        return maintenanceService.getMyRequests(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TENANT')")
    public MaintenanceRequestResponse getRequestById(@PathVariable Long id) {
        return maintenanceService.getRequestById(id);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public com.sep490.slms2026.dto.response.MaintenanceDashboardResponse getDashboardStats() {
        return maintenanceService.getDashboardStats();
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse acknowledge(@PathVariable Long id, @RequestBody MaintenanceAcknowledgeRequest request) {
        return maintenanceService.acknowledge(id, request);
    }

    @PutMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse schedule(@PathVariable Long id, @RequestBody MaintenanceScheduleRequest request) {
        return maintenanceService.schedule(id, request);
    }

    @PutMapping("/{id}/confirm-schedule")
    @PreAuthorize("hasRole('TENANT')")
    public MaintenanceRequestResponse confirmSchedule(@PathVariable Long id, @RequestBody MaintenanceConfirmScheduleRequest request) {
        return maintenanceService.confirmSchedule(id, request);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TENANT')")
    public MaintenanceRequestResponse updateStatus(@PathVariable Long id, @RequestBody MaintenanceStatusRequest request) {
        return maintenanceService.updateStatus(id, request);
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse resolve(@PathVariable Long id, @RequestBody MaintenanceResolveRequest request) {
        return maintenanceService.resolve(id, request);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN')") // host
    public MaintenanceRequestResponse approve(@PathVariable Long id, @RequestBody MaintenanceApproveRequest request) {
        return maintenanceService.approve(id, request);
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasRole('TENANT')")
    public MaintenanceRequestResponse confirm(@PathVariable Long id, @RequestBody MaintenanceConfirmRequest request) {
        return maintenanceService.confirm(id, request);
    }

    @PostMapping("/{id}/photos")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TENANT')")
    public MaintenanceRequestResponse uploadPhotos(
            @PathVariable Long id,
            @RequestParam("files") java.util.List<org.springframework.web.multipart.MultipartFile> files,
            @RequestParam("type") String type) {
        return maintenanceService.uploadPhotos(id, files, type);
    }
}
