package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.UpdateEquipmentStatusRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.service.EquipmentService;
import com.sep490.slms2026.service.MaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class GlobalEquipmentController {

    private final EquipmentService equipmentService;
    private final MaintenanceService maintenanceService;

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentResponse> getEquipmentById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getEquipmentById(id));
    }

    @GetMapping("/{id}/maintenance-history")
    public ResponseEntity<java.util.List<com.sep490.slms2026.dto.response.MaintenanceRequestResponse>> getEquipmentMaintenanceHistory(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.getEquipmentMaintenanceHistory(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<EquipmentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEquipmentStatusRequest request) {
        return ResponseEntity.ok(equipmentService.updateEquipmentStatus(id, request.getStatus()));
    }
}
