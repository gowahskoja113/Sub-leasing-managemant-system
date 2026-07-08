package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
