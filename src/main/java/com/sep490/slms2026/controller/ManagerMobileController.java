package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ResolveMaintenanceRequest;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import com.sep490.slms2026.repository.UserRepository;
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
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getAssignedRequests(Principal principal) {
        String username = principal.getName();
        User manager = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy manager: " + username));
        return ResponseEntity.ok(repository.findByAssignedManagerIdOrderByCreatedAtDesc(manager.getId()));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<?> assignRequest(@PathVariable UUID id, @RequestParam UUID managerId, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + username));
        maintenanceService.assignRequest(id, managerId, user.getId());
        return ResponseEntity.ok("Assigned successfully");
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @RequestParam MaintenanceStatus status,
            Principal principal) {
        String username = principal.getName();
        User manager = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy manager: " + username));
        maintenanceService.updateStatus(id, status, manager.getId());
        return ResponseEntity.ok("Status updated");
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> resolveRequest(@PathVariable UUID id, @RequestBody ResolveMaintenanceRequest dto,
            Principal principal) {
        String username = principal.getName();
        User manager = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy manager: " + username));
        maintenanceService.resolveRequest(id, dto, manager.getId());
        return ResponseEntity.ok("Resolved and financial integrated successfully");
    }
}