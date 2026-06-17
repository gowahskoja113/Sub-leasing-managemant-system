package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ResolveMaintenanceRequest;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/manager/maintenance")
@RequiredArgsConstructor
public class ManagerMobileController {

    private final MaintenanceService maintenanceService;
    private final MaintenanceRequestRepository repository;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getAssignedRequests(Principal principal) {
        UUID managerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(repository.findByAssignedManagerIdOrderByCreatedAtDesc(managerId));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<?> assignRequest(@PathVariable UUID id, @RequestParam UUID managerId, Principal principal) {
        UUID assignedBy = UUID.fromString(principal.getName());
        maintenanceService.assignRequest(id, managerId, assignedBy);
        return ResponseEntity.ok("Assigned successfully");
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @RequestParam MaintenanceStatus status, Principal principal) {
        UUID changedBy = UUID.fromString(principal.getName());
        maintenanceService.updateStatus(id, status, changedBy);
        return ResponseEntity.ok("Status updated");
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> resolveRequest(@PathVariable UUID id, @RequestBody ResolveMaintenanceRequest dto, Principal principal) {
        UUID managerId = UUID.fromString(principal.getName());
        maintenanceService.resolveRequest(id, dto, managerId);
        return ResponseEntity.ok("Resolved and financial integrated successfully");
    }
}