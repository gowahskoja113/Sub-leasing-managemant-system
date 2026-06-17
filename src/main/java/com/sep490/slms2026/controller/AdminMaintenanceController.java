package com.sep490.slms2026.controller;

import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/maintenance")
@RequiredArgsConstructor
public class AdminMaintenanceController {

    private final MaintenanceRequestRepository repository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllRequests(Pageable pageable) {
        Page<MaintenanceRequest> requests = repository.findAll(pageable);
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllRequestsNoPagination() {
        List<MaintenanceRequest> requests = repository.findAll();
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getRequestDetail(@PathVariable UUID id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboardStats() {
        long total = repository.count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("message", "Total Maintenance Requests");

        return ResponseEntity.ok(stats);
    }
}