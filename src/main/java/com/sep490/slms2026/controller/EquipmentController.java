package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.EquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    // ➕ Tạo thiết bị và gán vào phòng (kèm sinh QR tự động)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    @PostMapping
    public ResponseEntity<EquipmentResponse> createEquipment(
            @RequestBody EquipmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(equipmentService.createEquipment(request, userDetails.getId()));
    }

    // 📋 Danh sách thiết bị theo phòng
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping("/by-room/{roomId}")
    public ResponseEntity<Page<EquipmentResponse>> getByRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(equipmentService.getEquipmentByRoom(roomId, userDetails.getId(), pageable));
    }

    // 📋 Danh sách thiết bị theo property
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping("/by-property/{propertyId}")
    public ResponseEntity<Page<EquipmentResponse>> getByProperty(
            @PathVariable UUID propertyId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(equipmentService.getEquipmentByProperty(propertyId, userDetails.getId(), pageable));
    }

    // 🔍 Chi tiết thiết bị (kèm QR base64)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<EquipmentResponse> getDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(equipmentService.getEquipmentDetail(id, userDetails.getId()));
    }

    // ✏️ Cập nhật thông tin thiết bị
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<EquipmentResponse> updateEquipment(
            @PathVariable UUID id,
            @RequestBody EquipmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(equipmentService.updateEquipment(id, request, userDetails.getId()));
    }

    // ❌ Xoá thiết bị
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        equipmentService.deleteEquipment(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}