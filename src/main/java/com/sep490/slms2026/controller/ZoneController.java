package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ZoneRequest;
import com.sep490.slms2026.dto.response.ZoneResponse;
import com.sep490.slms2026.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    // ➕ Tạo mới Zone (Chỉ Admin hoặc Manager được phép)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody ZoneRequest request) {
        return new ResponseEntity<>(zoneService.createZone(request), HttpStatus.CREATED);
    }

    // 🔍 Lấy tất cả danh sách Zone (Tất cả user đã đăng nhập đều xem được để chọn khi làm việc)
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ZoneResponse>> getAllZones(Pageable pageable) {
        return ResponseEntity.ok(zoneService.getAllZones(pageable));
    }

    // 🔍 Lấy chi tiết một Zone bằng ID
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ZoneResponse> getZoneById(@PathVariable UUID id) {
        return ResponseEntity.ok(zoneService.getZoneById(id));
    }

    // ✏️ Cập nhật thông tin Zone (Chỉ Admin hoặc Manager)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ZoneResponse> updateZone(
            @PathVariable UUID id,
            @Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.ok(zoneService.updateZone(id, request));
    }

    // ❌ Xóa một Zone (Chỉ Admin mới có quyền xóa danh mục hệ thống)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id) {
        zoneService.deleteZone(id);
        return ResponseEntity.noContent().build();
    }
}