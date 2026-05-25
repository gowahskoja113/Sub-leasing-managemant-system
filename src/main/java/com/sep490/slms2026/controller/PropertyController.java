package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.PropertyRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.dto.ZoneSummaryProjection;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    // 📊 Thống kê Dashboard đổi lại role cho đúng đối tượng thụ hưởng là Manager/Admin
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<List<ZoneSummaryProjection>> getDashboardSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(propertyService.getManagerDashboard(userDetails.getId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PropertyResponse> createProperty(
            @RequestBody PropertyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(propertyService.createProperty(request, userDetails.getId()));
    }

    // 🔍 Lấy danh sách BĐS phân trang theo vùng được quản lý
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping
    public ResponseEntity<Page<PropertyResponse>> getProperties(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(propertyService.getPropertiesForManager(userDetails.getId(), pageable));
    }

    // ✏️ Cập nhật thông tin BĐS
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<PropertyResponse> updateProperty(
            @PathVariable UUID id,
            @RequestBody PropertyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(propertyService.updateProperty(id, request, userDetails.getId()));
    }

    // ❌ Xóa BĐS
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        propertyService.deleteProperty(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}