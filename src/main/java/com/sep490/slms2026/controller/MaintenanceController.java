package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.dto.response.OutstandingDamageResponse;
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

    @GetMapping("/outstanding-damages")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<OutstandingDamageResponse> getOutstandingDamages(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long tenantContractId) {
        return maintenanceService.getOutstandingDamages(propertyId, tenantContractId);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse approve(
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceApproveRequest request) {
        return maintenanceService.approve(id, request != null ? request : new MaintenanceApproveRequest());
    }

    @PutMapping("/{id}/reject-fault")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse rejectFault(
            @PathVariable Long id,
            @RequestBody MaintenanceRejectFaultRequest request) {
        return maintenanceService.rejectFault(id, request);
    }

    @PutMapping(value = "/{id}/submit-self-repair", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('TENANT')")
    public MaintenanceRequestResponse submitSelfRepairJson(
            @PathVariable Long id,
            @RequestBody MaintenanceSubmitSelfRepairRequest request) {
        return maintenanceService.submitSelfRepair(id, request, null);
    }

    @PutMapping(value = "/{id}/submit-self-repair", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT')")
    public MaintenanceRequestResponse submitSelfRepairMultipart(
            @PathVariable Long id,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        MaintenanceSubmitSelfRepairRequest request = new MaintenanceSubmitSelfRepairRequest();
        request.setNote(note);
        return maintenanceService.submitSelfRepair(id, request, files);
    }

    @PutMapping("/{id}/verify-repair")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse verifyRepair(
            @PathVariable Long id,
            @RequestBody MaintenanceVerifyRepairRequest request) {
        return maintenanceService.verifyRepair(id, request);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MaintenanceRequestResponse complete(
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceCompleteRequest request) {
        return maintenanceService.complete(id, request != null ? request : new MaintenanceCompleteRequest());
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TENANT')")
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
