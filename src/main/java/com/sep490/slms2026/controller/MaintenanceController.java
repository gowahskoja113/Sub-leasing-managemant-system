package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

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
    public MaintenanceRequestResponse createRequest(@RequestBody MaintenanceCreateRequest request) {
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
    public MaintenanceDashboardResponse getDashboardStats() {
        return maintenanceService.getDashboardStats();
    }

    /** Manager duyệt yêu cầu → chờ thợ ngoài sửa. */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse approve(
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceApproveRequest request) {
        return maintenanceService.approve(id, request != null ? request : new MaintenanceApproveRequest());
    }

    /** Manager báo sửa xong (cần ảnh AFTER). */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse complete(
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceCompleteRequest request) {
        return maintenanceService.complete(id, request != null ? request : new MaintenanceCompleteRequest());
    }

    /** Tenant xác nhận đã sửa xong. */
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasRole('TENANT')")
    public MaintenanceRequestResponse confirm(
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceConfirmRequest request) {
        return maintenanceService.confirm(id, request != null ? request : new MaintenanceConfirmRequest());
    }

    /** Tenant từ chối (JSON): reason + images URLs. */
    @PutMapping(value = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('TENANT')")
    public MaintenanceRequestResponse rejectJson(
            @PathVariable Long id,
            @RequestBody MaintenanceRejectRequest request) {
        return maintenanceService.reject(id, request, null);
    }

    /** Tenant từ chối (multipart): reason + files. */
    @PutMapping(value = "/{id}/reject", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT')")
    public MaintenanceRequestResponse rejectMultipart(
            @PathVariable Long id,
            @RequestParam("reason") String reason,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        MaintenanceRejectRequest request = new MaintenanceRejectRequest();
        request.setReason(reason);
        return maintenanceService.reject(id, request, files);
    }

    /**
     * Manager xem xét reject:
     * approve=true → APPROVED (sửa lại);
     * approve=false → WAITING_TENANT_CONFIRM (yêu cầu tenant xác nhận lại).
     */
    @PutMapping("/{id}/review-reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse reviewReject(
            @PathVariable Long id,
            @RequestBody MaintenanceApproveRequest request) {
        return maintenanceService.reviewReject(id, request);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return maintenanceService.cancel(id, reason);
    }

    @PostMapping("/{id}/photos")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TENANT')")
    public MaintenanceRequestResponse uploadPhotos(
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("type") String type) {
        return maintenanceService.uploadPhotos(id, files, type);
    }
}
