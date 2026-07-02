package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.service.EquipmentService;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
