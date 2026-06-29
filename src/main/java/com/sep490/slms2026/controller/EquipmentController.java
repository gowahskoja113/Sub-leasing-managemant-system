package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ReassignEquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/equipments")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @GetMapping
    public ResponseEntity<List<EquipmentResponse>> getEquipments(@PathVariable Long propertyId) {
        return ResponseEntity.ok(equipmentService.getEquipmentsByProperty(propertyId));
    }

    @DeleteMapping("/{equipmentId}")
    public ResponseEntity<Void> unassignEquipment(@PathVariable Long propertyId,
                                                  @PathVariable Long equipmentId) {
        equipmentService.unassignEquipment(propertyId, equipmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/v1/properties/{propertyId}/equipments/{equipmentId}/assign
     * Gán thiết bị từ kho (roomId = null) vào phòng mới.
     */
    @PatchMapping("/{equipmentId}/assign")
    public ResponseEntity<EquipmentResponse> reassignEquipment(
            @PathVariable Long propertyId,
            @PathVariable Long equipmentId,
            @Valid @RequestBody ReassignEquipmentRequest request) {
        return ResponseEntity.ok(equipmentService.reassignEquipment(propertyId, equipmentId, request));
    }

    /** POST /api/v1/properties/{propertyId}/equipments — Tạo thiết bị lắp thêm */
    @PostMapping
    public ResponseEntity<EquipmentResponse> createAddedEquipment(
            @PathVariable Long propertyId,
            @Valid @RequestBody com.sep490.slms2026.dto.request.CreateAddedEquipmentRequest request) {
        return ResponseEntity.ok(equipmentService.createAddedEquipment(propertyId, request));
    }
}
