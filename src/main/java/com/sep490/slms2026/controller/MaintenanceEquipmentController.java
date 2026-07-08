package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.EquipmentMaintenanceHistoryResponse;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Equipment Controller (non-property-scoped) — contract section 2.4.
 * Các endpoint theo /api/v1/equipment/... (khác với EquipmentController dùng
 * /api/v1/properties/{propertyId}/equipments).
 */
@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class MaintenanceEquipmentController {

    private final EquipmentService equipmentService;

    /** GET /api/v1/equipment/{id}/feature — chi tiết 1 thiết bị */
    @GetMapping("/{id}/feature")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<EquipmentResponse> getEquipmentById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getEquipmentById(id));
    }

    /** PUT /api/v1/equipment/{id}/feature — sửa thông tin thiết bị */
    @PutMapping("/{id}/feature")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<EquipmentResponse> updateEquipment(
            @PathVariable Long id,
            @RequestBody EquipmentResponse dto) {
        return ResponseEntity.ok(equipmentService.updateEquipment(id, dto));
    }

    /** PATCH /api/v1/equipment/{id}/status-feature — đổi lifecycle (GOOD/MAINTENANCE/BROKEN/DISPOSED) */
    @PatchMapping("/{id}/status-feature")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<EquipmentResponse> updateEquipmentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        EquipmentStatus status = EquipmentStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(equipmentService.updateEquipmentStatus(id, status));
    }

    /** GET /api/v1/equipment/feature?roomId= — thiết bị theo phòng (cho OM mobile) */
    @GetMapping("/feature")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<List<EquipmentResponse>> getEquipmentsByRoom(
            @RequestParam Long roomId) {
        return ResponseEntity.ok(equipmentService.getEquipmentsByRoom(roomId));
    }

    /** GET /api/v1/equipment/{id}/maintenance-history-feature — lịch sử bảo trì thiết bị */
    @GetMapping("/{id}/maintenance-history-feature")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<List<EquipmentMaintenanceHistoryResponse>> getEquipmentMaintenanceHistory(
            @PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getEquipmentMaintenanceHistory(id));
    }
}
