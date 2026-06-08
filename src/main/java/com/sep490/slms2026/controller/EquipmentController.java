package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.AddEquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/equipments")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @PostMapping
    public ResponseEntity<EquipmentResponse> addEquipment(
            @PathVariable Long propertyId,
            @Valid @RequestBody AddEquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(equipmentService.addEquipment(propertyId, request));
    }

    @GetMapping
    public ResponseEntity<List<EquipmentResponse>> getEquipments(@PathVariable Long propertyId) {
        return ResponseEntity.ok(equipmentService.getEquipmentsByProperty(propertyId));
    }
}
