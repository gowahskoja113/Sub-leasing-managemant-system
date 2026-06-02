package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.EquipmentAssignRequest;
import com.sep490.slms2026.dto.request.EquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/equipments")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    /**
     * POST /api/v1/equipments
     * Tạo thiết bị mới.
     * Body có thể kèm roomId hoặc propertyId để gán ngay khi tạo.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EquipmentResponse> create(
            @Valid @RequestBody EquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(equipmentService.create(request));
    }

    /**
     * GET /api/v1/equipments/{id}
     * Chi tiết 1 thiết bị.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EquipmentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(equipmentService.getById(id));
    }

    /**
     * PUT /api/v1/equipments/{id}
     * Cập nhật thông tin thiết bị (tên, danh mục, trạng thái, v.v.).
     * Không dùng endpoint này để gán phòng — dùng /assign.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EquipmentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody EquipmentRequest request) {
        return ResponseEntity.ok(equipmentService.update(id, request));
    }

    /**
     * DELETE /api/v1/equipments/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        equipmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────
    // QUERY BY LOCATION
    // ──────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/equipments/room/{roomId}
     * Lấy tất cả thiết bị trong 1 phòng.
     */
    @GetMapping("/room/{roomId}")
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<EquipmentResponse>> getByRoom(@PathVariable UUID roomId) {
        return ResponseEntity.ok(equipmentService.getByRoom(roomId));
    }

    /**
     * GET /api/v1/equipments/property/{propertyId}
     * Lấy tất cả thiết bị thuộc 1 property (toàn bộ các phòng).
     */
    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<EquipmentResponse>> getByProperty(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(equipmentService.getByProperty(propertyId));
    }

    // ──────────────────────────────────────────────────────────────
    // ASSIGNMENT
    // ──────────────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/equipments/{id}/assign
     * Gán hoặc chuyển thiết bị sang phòng / property khác.
     *
     * Body:
     * - { "roomId": "..." }            → gán vào phòng cụ thể
     * - { "propertyId": "..." }        → gán vào nhà nguyên căn (whole-house)
     * - { }                            → bỏ gán hoàn toàn
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EquipmentResponse> assign(
            @PathVariable UUID id,
            @RequestBody EquipmentAssignRequest assignRequest) {
        return ResponseEntity.ok(equipmentService.assign(id, assignRequest));
    }

    // ──────────────────────────────────────────────────────────────
    // QR CODE
    // ──────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/equipments/qr?payload={qrPayload}
     * Lấy thông tin thiết bị từ QR payload — dùng cho màn hình bảo trì khi scan QR.
     */
    @GetMapping("/qr")
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EquipmentResponse> getByQrPayload(
            @RequestParam String payload) {
        return ResponseEntity.ok(equipmentService.getByQrPayload(payload));
    }
}