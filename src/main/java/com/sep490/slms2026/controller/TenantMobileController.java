package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateMaintenanceRequest;
import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/maintenance")
@RequiredArgsConstructor
public class TenantMobileController {

    private final MaintenanceService maintenanceService;
    private final MaintenanceRequestRepository repository;

    @PostMapping
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<?> createRequest(@RequestBody CreateMaintenanceRequest dto, Principal principal) {
        UUID tenantId = UUID.fromString(principal.getName()); // Giả sử username là ID
        return ResponseEntity.ok(maintenanceService.createRequest(dto, tenantId));
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<?> getMyRequests(Principal principal) {
        UUID tenantId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(repository.findByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<?> getRequestDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(repository.findById(id).orElse(null));
    }
}