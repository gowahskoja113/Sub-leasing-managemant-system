package com.sep490.slms2026.controller;

import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/maintenance")
@RequiredArgsConstructor
public class AdminMaintenanceController {

    private final MaintenanceRequestRepository repository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllRequests() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getRequestDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(repository.findById(id).orElse(null));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboardStats() {
        // Có thể bổ sung Query đếm tổng số lượng pending, resolved... tại Repository
        long total = repository.count();
        return ResponseEntity.ok("Total Maintenance Requests: " + total);
    }
}