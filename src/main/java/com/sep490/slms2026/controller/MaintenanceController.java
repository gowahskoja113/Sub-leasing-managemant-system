package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService service;

    @GetMapping
    public Page<MaintenanceRequestResponse> getRequests(Pageable pageable) {
        return service.getRequests(pageable);
    }

    @PutMapping("/{id}/acknowledge")
    public MaintenanceRequestResponse acknowledge(@PathVariable Long id, @RequestBody MaintenanceAcknowledgeRequest request) {
        return service.acknowledge(id, request);
    }

    @PutMapping("/{id}/schedule")
    public MaintenanceRequestResponse schedule(@PathVariable Long id, @RequestBody MaintenanceScheduleRequest request) {
        return service.schedule(id, request);
    }

    @PutMapping("/{id}/confirm-schedule")
    public MaintenanceRequestResponse confirmSchedule(@PathVariable Long id, @RequestBody MaintenanceConfirmScheduleRequest request) {
        return service.confirmSchedule(id, request);
    }

    @PutMapping("/{id}/status")
    public MaintenanceRequestResponse updateStatus(@PathVariable Long id, @RequestBody MaintenanceStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @PutMapping("/{id}/resolve")
    public MaintenanceRequestResponse resolve(@PathVariable Long id, @RequestBody MaintenanceResolveRequest request) {
        return service.resolve(id, request);
    }

    @PutMapping("/{id}/approve")
    public MaintenanceRequestResponse approve(@PathVariable Long id, @RequestBody MaintenanceApproveRequest request) {
        return service.approve(id, request);
    }

    @PutMapping("/{id}/confirm")
    public MaintenanceRequestResponse confirm(@PathVariable Long id, @RequestBody MaintenanceConfirmRequest request) {
        return service.confirm(id, request);
    }

    @PostMapping("/{id}/photos")
    public MaintenanceRequestResponse uploadPhotos(
            @PathVariable Long id,
            @RequestParam("files") java.util.List<org.springframework.web.multipart.MultipartFile> files,
            @RequestParam("type") String type) {
        return service.uploadPhotos(id, files, type);
    }
}
