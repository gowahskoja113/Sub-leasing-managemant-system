package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipments")
@RequiredArgsConstructor
public class EquipmentQrController {

    private final EquipmentService equipmentService;

    @GetMapping("/by-qr/{qrCode}")
    @PreAuthorize("hasAnyRole('TENANT','MANAGER','ADMIN')")
    public ResponseEntity<EquipmentResponse> getByQrCode(@PathVariable String qrCode) {
        return ResponseEntity.ok(equipmentService.getEquipmentByQrCode(qrCode));
    }
}
