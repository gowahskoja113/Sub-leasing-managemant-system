package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateMaintenanceRequest;
import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.MaintenanceService;
import com.sep490.slms2026.entity.User;
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
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<?> createRequest(@RequestBody CreateMaintenanceRequest dto, Principal principal) {
        // Lấy username từ Principal
        String username = principal.getName();

        // Tìm User từ username để lấy ID
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + username));

        // Tạo maintenance request với tenantId
        return ResponseEntity.ok(maintenanceService.createRequest(dto, user.getId()));
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<?> getMyRequests(Principal principal) {
        // 1. Lấy username từ Principal
        String username = principal.getName();

        // 2. Tìm User từ username để lấy ID chính xác
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + username));

        // 3. Giả sử trong User có thông tin liên kết với Tenant hoặc dùng ID của User
        // làm Tenant ID
        UUID tenantId = user.getId();

        return ResponseEntity.ok(repository.findByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<?> getRequestDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(repository.findById(id).orElse(null));
    }
}